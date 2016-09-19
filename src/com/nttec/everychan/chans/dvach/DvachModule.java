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

package com.nttec.everychan.chans.dvach;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.nttec.everychan.R;
import com.nttec.everychan.api.AbstractWakabaModule;
import com.nttec.everychan.api.interfaces.CancellableTask;
import com.nttec.everychan.api.interfaces.ProgressListener;
import com.nttec.everychan.api.models.BoardModel;
import com.nttec.everychan.api.models.CaptchaModel;
import com.nttec.everychan.api.models.DeletePostModel;
import com.nttec.everychan.api.models.PostModel;
import com.nttec.everychan.api.models.SendPostModel;
import com.nttec.everychan.api.models.SimpleBoardModel;
import com.nttec.everychan.api.models.UrlPageModel;
import com.nttec.everychan.api.util.WakabaReader;
import com.nttec.everychan.api.util.LazyPreferences;
import com.nttec.everychan.api.util.UrlPathUtils;
import com.nttec.everychan.api.util.WakabaUtils;
import com.nttec.everychan.common.Async;
import com.nttec.everychan.common.IOUtils;
import com.nttec.everychan.common.Logger;
import com.nttec.everychan.common.MainApplication;
import com.nttec.everychan.http.ExtendedMultipartBuilder;
import com.nttec.everychan.http.streamer.HttpRequestModel;
import com.nttec.everychan.http.streamer.HttpResponseModel;
import com.nttec.everychan.http.streamer.HttpStreamer;
import com.nttec.everychan.http.streamer.HttpWrongStatusCodeException;

public class DvachModule extends AbstractWakabaModule {
    private static final String TAG = "DvachModule";
    
    static final String CHAN_NAME = "2-chru.net";
    private static final String DEFAULT_DOMAIN = "2-chru.net";
    private static final String ONION_DOMAIN = "dmirrgetyojz735v.onion";
    private static final String DOMAINS_HINT = "2-chru.net, mirror.2-chru.net, bypass.2-chru.net";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN, "mirror.2-chru.net", "bypass.2-chru.net", "2chru.net",
            "2chru.cafe", "2-chru.cafe" };
    private static final String[] FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm", "mp4", "ogv", "mp3", "ogg" };
    
    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2>(.*?)</h2>", Pattern.DOTALL);
    private static final Pattern REDIRECT_PATTERN = Pattern.compile("url=res/(\\d+)\\.html");
    
    public DvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (2-chru.net)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dvach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        if (preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false)) return ONION_DOMAIN;
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        for (int i=0; i<DOMAINS.length; ++i) domains[i] = DOMAINS[i];
        domains[DOMAINS.length] = domain;
        return domains;
    }
    
    @Override
    protected boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        CheckBoxPreference onionPref = new LazyPreferences.CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        domainPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "menu.html";
        HttpResponseModel responseModel = null;
        DvachBoardsListReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(oldBoardsList != null).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new DvachBoardsListReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readBoardsList();
            } else {
                if (responseModel.notModified()) return null;
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    protected Map<String, SimpleBoardModel> getBoardsMap(ProgressListener listener, CancellableTask task) throws Exception {
        try {
            return super.getBoardsMap(listener, task);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "Аноним";
        board.timeZoneId = "GMT+3";
        board.searchAllowed = true;
        
        board.readonlyBoard = false;
        board.requiredFileForNewThread = !shortName.equals("d");
        board.allowDeletePosts = true;
        board.allowDeleteFiles = false;
        board.allowNames = !shortName.equals("b");
        board.allowSubjects = true;
        board.allowSage = false;
        board.allowEmails = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = shortName.equals("d") ? 0 : 1;
        board.attachmentsFormatFilters = FORMATS;
        board.markType = BoardModel.MARK_WAKABAMARK;
        
        return board;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new DvachReader(stream);
    }
    
    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + boardName + "/search?q=" + URLEncoder.encode(searchRequest, "UTF-8");
        HttpResponseModel responseModel = null;
        DvachSearchReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.DEFAULT_GET;
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new DvachSearchReader(responseModel.stream, this);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readSerachPage();
            } else {
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            String checkUrl = getUsingUrl() + boardName + "/api/requires-captcha";
            if (HttpStreamer.getInstance().
                    getJSONObjectFromUrl(checkUrl, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, false).
                    getString("requires-captcha").equals("0")) return null;
        } catch (Exception e) {
            Logger.e(TAG, "captcha", e);
        }
        String captchaUrl = getUsingUrl() + boardName + "/captcha?" + String.valueOf(Math.floor(Math.random() * 10000000));
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/post";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("parent", model.threadNumber != null ? model.threadNumber : "0").
                addString("name", model.name).
                addString("email", model.email).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("captcha", TextUtils.isEmpty(model.captchaAnswer) ? "" : model.captchaAnswer).
                addString("password", model.password);
        if (model.threadNumber != null) postEntityBuilder.addString("noko", "on");
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        
        try {
            cssTest(model.boardName, task);
        } catch (Exception e) {
            Logger.e(TAG, "csstest failed", e);
        }
        
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("ОБНОВЛ")) {
                    if (model.threadNumber == null) {
                        Matcher redirectMatcher = REDIRECT_PATTERN.matcher(htmlResponse);
                        if (redirectMatcher.find()) {
                            UrlPageModel redirModel = new UrlPageModel();
                            redirModel.chanName = CHAN_NAME;
                            redirModel.type = UrlPageModel.TYPE_THREADPAGE;
                            redirModel.boardName = model.boardName;
                            redirModel.threadNumber = redirectMatcher.group(1);
                            return buildUrl(redirModel);
                        }
                    }
                    return null;
                }
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) {
                    throw new Exception(errorMatcher.group(1));
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("posts[]", model.postNumber));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Удалить"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Неверный пароль")) throw new Exception("Неверный пароль");
        return null;
    }
    
    private void cssTest(String boardName, final CancellableTask task) throws Exception { /* =*.*= */
        class CSSCodeHolder {
            private volatile String cssCode = null;
            public synchronized void setCode(String code) {
                Logger.d(TAG, "set CSS code: " + code);
                if (cssCode == null) cssCode = code;
            }
            public boolean isSet() {
                return cssCode != null;
            }
            public String getCode() {
                return cssCode;
            }
        }
        class WebViewHolder {
            private WebView webView = null;
        }
        
        final CSSCodeHolder holder = new CSSCodeHolder();
        final WebViewHolder wv = new WebViewHolder();
        final String cssTest = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + boardName + "/csstest.foo", HttpRequestModel.DEFAULT_GET,
                httpClient, null, task, false);
        long startTime = System.currentTimeMillis();
        
        Async.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wv.webView = new WebView(MainApplication.getInstance());
                wv.webView.setWebViewClient(new WebViewClient(){
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        if (url.contains("?code=") && !task.isCancelled()) {
                            holder.setCode(url.substring(url.indexOf("?code=") + 6));
                        }
                    }
                });
                wv.webView.loadDataWithBaseURL("http://127.0.0.1/csstest.foo", cssTest, "text/html", "UTF-8", "");
            }
        });
        
        while (!holder.isSet()) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > 5000) break;
            Thread.yield();
        }
        
        Async.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    wv.webView.stopLoading();
                    wv.webView.clearCache(true);
                    wv.webView.destroy();
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
        });
        
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        String cssCode = holder.getCode();
        if (cssCode != null) {
            HttpStreamer.getInstance().getBytesFromUrl(getUsingUrl() + boardName + "/csstest.foo?code=" + cssCode, HttpRequestModel.DEFAULT_GET,
                    httpClient, null, task, false);
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        try {
            if (model.type == UrlPageModel.TYPE_SEARCHPAGE)
                return getUsingUrl() + model.boardName + "/search?q=" + URLEncoder.encode(model.searchRequest, "UTF-8");
        } catch (Exception e) {}
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath = UrlPathUtils.getUrlPath(url, getAllDomains());
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        if (url.contains("/search?q=")) {
            try {
                int index = url.indexOf("/search?q=");
                String left = url.substring(0, index);
                UrlPageModel model = new UrlPageModel();
                model.chanName = CHAN_NAME;
                model.type = UrlPageModel.TYPE_SEARCHPAGE;
                model.boardName = left.substring(left.lastIndexOf('/') + 1);
                model.searchRequest = url.substring(index + 10);
                model.searchRequest = URLDecoder.decode(model.searchRequest, "UTF-8");
                return model;
            } catch (Exception e) {}
        }
        return WakabaUtils.parseUrlPath(urlPath, getChanName());
    }
}
