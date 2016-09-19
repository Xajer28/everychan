/*
 * Everychan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nttec.everychan.chans.krautchan;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nttec.everychan.api.models.AttachmentModel;
import com.nttec.everychan.api.models.BadgeIconModel;
import com.nttec.everychan.api.models.PostModel;
import com.nttec.everychan.api.models.ThreadModel;
import com.nttec.everychan.api.util.CryptoUtils;
import com.nttec.everychan.api.util.RegexUtils;
import com.nttec.everychan.common.Logger;

import org.apache.commons.lang3.StringEscapeUtils;

public class KrautReader implements Closeable {
    private static final String TAG = "KrautReader";
    private static final boolean LINKIFY = true;
    
    private static final DateFormat KRAUT_DATEFORMAT;
    static {
        KRAUT_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        KRAUT_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    }
    
    private static final Pattern ATTACHMENT_FILENAME_PATTERN =
            Pattern.compile("<span id=\"filename_[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern ATTACHMENT_LINKS_PATTERN =
            Pattern.compile("a\\s+href=\"/files/(\\d+\\..+?)\"(?:.+?src=\"?/thumbnails/(\\d+\\..+?)\\s\"?)?", Pattern.DOTALL);
    private static final Pattern ATTACHMENT_INFO_PATTERN =
            Pattern.compile("<span class=\"fileinfo\">\\s*(.*?),\\s*(.*?),\\s*(.*?)</span>", Pattern.DOTALL);
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)[x×х](\\d+)"); // \u0078 \u00D7 \u0445
    private static final Pattern ATTACHMENT_SIZE_PATTERN = Pattern.compile("([,\\.\\d]+) ?([km])?b", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern ICON_DESCRIPTION_PATTERN = Pattern.compile("helpTip\\('([^']*)'");
    
    private static final Pattern BAN_MARK_PATTERN = Pattern.compile("<span class=\"ban_mark\">([^<]*)</span>");
    
    private static final char[] THREAD_START = "id=\"thread_".toCharArray();
    private static final char[] BLOCKQUOTE_CLOSE = "</blockquote>".toCharArray();
    
    private static final int FILTER_THREAD_END = 0;
    private static final int FILTER_POSTNUMBER = 1;
    private static final int FILTER_COUNTRYBALL = 2;
    private static final int FILTER_COUNTRYBALL_WAR = 3;
    private static final int FILTER_SUBJECT = 4;
    private static final int FILTER_POSTERNAME = 5;
    private static final int FILTER_TRIPCODE = 6;
    private static final int FILTER_ADMINMARK = 7;
    private static final int FILTER_DATE = 8;
    private static final int FILTER_SAGE = 9;
    private static final int FILTER_ATTACHMENT = 10;
    private static final int FILTER_ATTACHMENT_OP = 11;
    private static final int FILTER_START_COMMENT = 12;
    private static final int FILTER_OMITTEDPOSTS = 13;
    
    public static final char[][] FILTERS_OPEN = {
        "class=\"thread\"".toCharArray(),
        //"<div class=\"postheader\">".toCharArray(),
        "<input name=\"post_".toCharArray(),
        "<img src=\"/images/balls/".toCharArray(),
        "<img src=\"/images/warballs/".toCharArray(),
        "<span class=\"postsubject\">".toCharArray(),
        "<span class=\"postername\">".toCharArray(),
        "<span class=\"tripcode\">".toCharArray(),
        "<span class=\"authority_".toCharArray(),
        "<span class=\"postdate\">".toCharArray(),
        //"<span class=\"postnumber\"".toCharArray(),
        "<span class=\"sage\">".toCharArray(),
        "div class=\"file_reply\">".toCharArray(),
        "div class=\"file_thread\">".toCharArray(),
        "<p id=\"post_text_".toCharArray(),
        "<span class=\"omittedinfo\">".toCharArray(),
    };
    
    private static final char[][] FILTERS_CLOSE = {
        null,
        "\"".toCharArray(),
        ">".toCharArray(),
        ">".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        null, 
        "<blockquote>".toCharArray(),
        "<blockquote>".toCharArray(),
        ">".toCharArray(),
        "</span>".toCharArray()
    };
    
    private final Reader _in;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private StringBuilder commentBuffer = new StringBuilder();
    private StringBuilder omittedDigitsBuffer = new StringBuilder();
    private List<AttachmentModel> currentAttachments;
    
    public KrautReader(Reader reader) {
        _in = reader;
    }
    
    public KrautReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        initPostModel();
        skipUntilSequence(THREAD_START);
        readData();
        
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
        }
        finalizeThread();
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        postsBuf = new ArrayList<PostModel>();
    }
    
    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.trip = "";
        currentAttachments = new ArrayList<AttachmentModel>();
    }
    
    private void finalizeThread() {
        if (postsBuf.size() > 0) {
            currentThread.posts = postsBuf.toArray(new PostModel[postsBuf.size()]);
            currentThread.threadNumber = currentThread.posts[0].number;
            for (PostModel post : currentThread.posts) post.parentThread = currentThread.threadNumber;
            threads.add(currentThread);
            initThreadModel();
        }
    }
    
    private void finalizePost() {
        if (currentPost.number != null && currentPost.number.length() > 0) {
            ++currentThread.postsCount;
            currentPost.attachments = currentAttachments.toArray(new AttachmentModel[currentAttachments.size()]);
            if (currentPost.name == null) currentPost.name = "";
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            if (currentPost.email == null) currentPost.email = "";
            if (currentPost.trip == null) currentPost.trip = "";
            postsBuf.add(currentPost);
        }
        initPostModel();
    }
    
    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_POSTNUMBER:
                currentPost.number = readUntilSequence(FILTERS_CLOSE[filterIndex]).trim();
                break;
            case FILTER_COUNTRYBALL:
            case FILTER_COUNTRYBALL_WAR:
                parseIcon(readUntilSequence(FILTERS_CLOSE[filterIndex]), filterIndex == FILTER_COUNTRYBALL_WAR);
                break;
            case FILTER_SUBJECT:
                currentPost.subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                currentPost.subject = CryptoUtils.fixCloudflareEmails(currentPost.subject);
                break;
            case FILTER_POSTERNAME:
                currentPost.name = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_TRIPCODE:
                currentPost.trip += StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_ADMINMARK:
                skipUntilSequence(">".toCharArray());
                currentPost.trip += StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_DATE:
                String date = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                int ms = 0;
                try {
                    int dotPosition = date.lastIndexOf('.');
                    if (dotPosition != -1) {
                        ms = Integer.parseInt(date.substring(dotPosition + 1)) / 1000;
                        date = date.substring(0, dotPosition);
                    }
                } catch (NumberFormatException e) {}
                try {
                    currentPost.timestamp = KRAUT_DATEFORMAT.parse(date).getTime() + ms;
                } catch (Exception e) {
                    Logger.e(TAG, "unable to parse date", e);
                }
                break;
            case FILTER_SAGE:
                currentPost.sage = true;
                break;
            case FILTER_ATTACHMENT:
            case FILTER_ATTACHMENT_OP:
                String[] attachments = readUntilSequence(FILTERS_CLOSE[filterIndex]).split("</div>");
                for (String attachment : attachments) parseAttachment(attachment);
                break;
            case FILTER_START_COMMENT:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.comment = readPostComment();
                finalizePost();
                break;
            case FILTER_OMITTEDPOSTS:
                parseOmittedString(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
        }
    }
    
    private String readPostComment() throws IOException {
        commentBuffer.setLength(0);
        commentBuffer.append("<p>");
        int len = BLOCKQUOTE_CLOSE.length;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            commentBuffer.append((char) curChar);
            if (curChar == BLOCKQUOTE_CLOSE[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == BLOCKQUOTE_CLOSE[0] ? 1 : 0;
            }
        }
        int buflen = commentBuffer.length();
        if (buflen > len) {
            commentBuffer.setLength(buflen - len);
            String comment = RegexUtils.replaceAll(commentBuffer, BAN_MARK_PATTERN, "<b><font color=\"red\">$1</font></b>");
            if (LINKIFY) comment = RegexUtils.linkify(comment);
            return CryptoUtils.fixCloudflareEmails(comment);
        } else {
            return "";
        }
    }
    
    private void parseOmittedString(String omitted) {
        int postsOmitted = -1;
        int filesOmitted = -1;
        try {
            int len = omitted.length();
            for (int i=0; i<=len; ++i) {
                char ch = i == len ? ' ' : omitted.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    omittedDigitsBuffer.append(ch);
                } else {
                    if (omittedDigitsBuffer.length() > 0) {
                        int parsedValue = Integer.parseInt(omittedDigitsBuffer.toString());
                        omittedDigitsBuffer.setLength(0);
                        if (postsOmitted == -1) postsOmitted = parsedValue;
                        else filesOmitted = parsedValue;
                    }
                }
            }
        } catch (NumberFormatException e) {}
        if (postsOmitted > 0) currentThread.postsCount += postsOmitted;
        if (filesOmitted > 0) currentThread.attachmentsCount += filesOmitted;
    }
    
    private void parseAttachment(String html) {
        Matcher attachmentMatcher = ATTACHMENT_LINKS_PATTERN.matcher(html);
        if (attachmentMatcher.find()) {
            AttachmentModel model = new AttachmentModel();
            model.type = AttachmentModel.TYPE_OTHER_FILE;
            model.size = -1;
            model.width = -1;
            model.height = -1;
            model.path = "/files/" + attachmentMatcher.group(1);
            String thumbnailGroup = attachmentMatcher.group(2);
            model.thumbnail = thumbnailGroup == null ? null : "/thumbnails/" + thumbnailGroup;
            String ext = model.path.substring(model.path.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")) model.type = AttachmentModel.TYPE_IMAGE_STATIC;
            else if (ext.equals("gif")) model.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (ext.equals("webm")) model.type = AttachmentModel.TYPE_VIDEO;
            else if (ext.equals("mp3") || ext.equals("ogg")) model.type = AttachmentModel.TYPE_AUDIO;
            Matcher origFilenameMatcher = ATTACHMENT_FILENAME_PATTERN.matcher(html);
            if (origFilenameMatcher.find()) {
                model.originalName = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(origFilenameMatcher.group(1)).trim());
            }
            Matcher infoMatcher = ATTACHMENT_INFO_PATTERN.matcher(html);
            if (infoMatcher.find()) {
                Matcher pxSizeMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(infoMatcher.group(2));
                if (pxSizeMatcher.find()) {
                    try {
                        int width = Integer.parseInt(pxSizeMatcher.group(1));
                        int height = Integer.parseInt(pxSizeMatcher.group(2));
                        model.width = width;
                        model.height = height;
                    } catch (NumberFormatException e) {}
                }
                Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(infoMatcher.group(3));
                if (byteSizeMatcher.find()) {
                    try {
                        String digits = byteSizeMatcher.group(1).replace(',', '.');
                        int multiplier = 1;
                        String prefix = byteSizeMatcher.group(2);
                        if (prefix != null) {
                            if (prefix.equalsIgnoreCase("k")) multiplier = 1024;
                            else if (prefix.equalsIgnoreCase("m")) multiplier = 1024 * 1024;
                        }
                        int value = Math.round(Float.parseFloat(digits) / 1024 * multiplier);
                        model.size = value;
                    } catch (NumberFormatException e) {}
                }
            }
            ++currentThread.attachmentsCount;
            currentAttachments.add(model);
        }
    }
    
    private void parseIcon(String html, boolean warball) {
        int fqp = html.indexOf('\"');
        if (fqp != -1) {
            BadgeIconModel model = new BadgeIconModel();
            model.source = (warball ? "/images/warballs/" : "/images/balls/") + html.substring(0, fqp);
            Matcher descMatcher = ICON_DESCRIPTION_PATTERN.matcher(html);
            if (descMatcher.find()) model.description = descMatcher.group(1);
            currentPost.icons = new BadgeIconModel[] { model };
        }
    }
    
    private void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
    }
    
    private String readUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return "";
        readBuffer.setLength(0);
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            readBuffer.append((char) curChar);
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
        int buflen = readBuffer.length();
        if (buflen >= len) {
            readBuffer.setLength(buflen - len);
            return readBuffer.toString();
        } else {
            return "";
        }
    }
    
    @Override
    public void close() throws IOException {
        _in.close();
    }
}
