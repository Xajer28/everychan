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

package nya.miku.wishmaster.chans.chan10;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

public class Chan10Module extends AbstractKusabaModule {
    private static final String TAG = "Chan10Module";
    
    private static final String CHAN_NAME = "10ch.ru";
    private static final String DOMAIN = "10ch.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Анимация", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Работа сайта",  " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "conf", "Конференц-зал", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "edit", "Редакция", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bc", "Обсуждение", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ad", "Links", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "o", "Other", " ", false)
    };
    
    public Chan10Module(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Десятый канал (10ch.ru)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_10ch, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        switch (shortName) {
            case "a": model.defaultUserName = "Ko-tan"; break;
            case "edit": model.defaultUserName = "Unregistered"; break;
            case "bc": model.defaultUserName = "Журналист"; break;
            case "ad": model.defaultUserName = "Spamer"; break;
            case "o": model.defaultUserName = "Чатер-аватарка"; break;
            default: model.defaultUserName = "Саша"; break;
        }
        model.timeZoneId = "GMT+3";
        model.requiredFileForNewThread = shortName.equals("a") || shortName.equals("b") || shortName.equals("edit");
        model.allowNames = !shortName.equals("b") && !shortName.equals("d") && !shortName.equals("a");
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        return new Chan10Reader(stream, canCloudflare());
    }
    
    @Override
    protected void setSendPostEntity(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        setSendPostEntityMain(model, postEntityBuilder);
        setSendPostEntityAttachments(model, postEntityBuilder);
        postEntityBuilder.addString("embed", "");
        setSendPostEntityPassword(model, postEntityBuilder);
        postEntityBuilder.addString("redirecttothread", "1");
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Отправить";
    }
    
    @SuppressLint("SimpleDateFormat")
    private static class Chan10Reader extends KusabaReader {
        private static final DateFormat DATE_FORMAT;
        static {
            DateFormatSymbols symbols = new DateFormatSymbols();
            symbols.setMonths(new String[] {
                    "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня", "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"});
            DATE_FORMAT = new SimpleDateFormat("dd MMMM yy HH:mm:ss", symbols);
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        }
        
        private static final char[] IFRAME_FILTER = "<iframe".toCharArray();
        private static final Pattern IFRAME_SRC = Pattern.compile("src=\"([^\"]*)\"");
        
        public Chan10Reader(InputStream in, boolean canCloudflare) {
            super(in, DATE_FORMAT, canCloudflare, ~(FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS|FLAG_OMITTED_STRING_REMOVE_HREF));
        }
        
        private int curIframePos = 0;
        
        @Override
        protected void customFilters(int ch) throws IOException {
            super.customFilters(ch);
            
            if (ch == IFRAME_FILTER[curIframePos]) {
                ++curIframePos;
                if (curIframePos == IFRAME_FILTER.length) {
                    Matcher srcMatcher = IFRAME_SRC.matcher(readUntilSequence(">".toCharArray()));
                    if (srcMatcher.find()) {
                        AttachmentModel attachment = new AttachmentModel();
                        attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                        attachment.path = srcMatcher.group(1);
                        attachment.size = -1;
                        if (attachment.path.startsWith("//")) attachment.path = "http:" + attachment.path;
                        if (attachment.path.contains("coub.com/embed/")) {
                            int qIndex = attachment.path.indexOf('?');
                            if (qIndex > 0) attachment.path = attachment.path.substring(0, qIndex);
                            attachment.path = attachment.path.replace("/embed/", "/view/");
                        }
                        currentAttachments.add(attachment);
                    }
                    curIframePos = 0;
                }
            } else {
                if (curIframePos != 0) curIframePos = ch == IFRAME_FILTER[0] ? 1 : 0;
            }
        }
        
        @Override
        protected void parseAttachment(String html) {
            int lastLinkIndex = html.lastIndexOf("href=\"");
            if (lastLinkIndex < 0) return;
            super.parseAttachment(html.substring(lastLinkIndex));
        }
        
        @Override
        protected void parseThumbnail(String imgTag) {
            super.parseThumbnail(imgTag);
            
            try {
                int sIndex = 0;
                while (imgTag.charAt(sIndex) <= ' ') ++sIndex;
                if (imgTag.startsWith("id=\"start_video", sIndex)) {
                    String id = imgTag.substring(sIndex + 15, imgTag.indexOf('"', sIndex + 15));
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.path = "http://youtube.com/watch?v=" + id;
                    attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
                    attachment.size = -1;
                    currentAttachments.add(attachment);
                }
            } catch (Exception e) { //string array bounds
                Logger.e(TAG, e);
            }
        }
        
        @Override
        protected void parseDate(String date) {
            date = date.replaceAll("(?:[^\\d]*)(\\d(?:.*))", "$1");
            super.parseDate(date);
        }
        
    }
    
}
