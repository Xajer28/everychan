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

package com.nttec.everychan.chans.vichan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import java.io.OutputStream;
import com.nttec.everychan.R;
import com.nttec.everychan.api.AbstractVichanModule;
import com.nttec.everychan.api.interfaces.CancellableTask;
import com.nttec.everychan.api.interfaces.ProgressListener;
import com.nttec.everychan.api.models.BoardModel;
import com.nttec.everychan.api.models.SendPostModel;
import com.nttec.everychan.api.models.SimpleBoardModel;
import com.nttec.everychan.api.models.UrlPageModel;
import com.nttec.everychan.api.util.ChanModels;
import com.nttec.everychan.http.streamer.HttpWrongStatusCodeException;

public class VichanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "pl.vichan.net";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Radom", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cp", "Chłodne Pasty", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "id", "Inteligentne dyskusje", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "True International", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r+oc", "Prośby + Oryginalna zawartość", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "slav", "Słowianie", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "veto", "Wolne! Nie pozwalam!", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "waifu", "Waifu i husbando", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wiz", "Mizoginia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "btc", "Biznes i ekonomia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Informatyka, Elektronika, Gadżety", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c++", "Zaawansowana informatyka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fso", "Motoryzacja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hobby i zainteresowania", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "kib", "Kibice", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ku", "Kuchnia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lsd", "Substancje psychoaktywne", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psl", "Polityka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Nauka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trv", "Podróże", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Gry video i online", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime i Manga", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ac", "Komiks i animacja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "az", "Azja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fr", "Filozofia & religia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hk", "Historia & Kultura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literatura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Muzyka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Filmy i seriale", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vp", "Pokémon", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Wróżbita Maciej", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "med", "Medyczny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Prawny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pro", "Problemy i protipy", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psy", "Psychologia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sex", "Seks i związki", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "soc", "Socjalizacja i atencja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sr", "Samorozwój", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "swag", "Styl i wygląd", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trap", "Transseksualizm", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "chan", "Chany i ich kultura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "meta", "Administracyjny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mit", "Mitomania", " ", false),
    };

    public VichanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Polski vichan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_vichan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return "pl.vichan.net";
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            super.downloadFile(url, out, listener, task);
        } catch (HttpWrongStatusCodeException e) {
            if (url.contains("/thumb/") && url.endsWith(".jpg") && e.getStatusCode() == 404) {
                super.downloadFile(url.substring(0, url.length() - 3) + "gif", out, listener, task);
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replaceAll("-\\w+.*html", ".html"));
    }

    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        return super.fixRelativeUrl(url);
    }
}
