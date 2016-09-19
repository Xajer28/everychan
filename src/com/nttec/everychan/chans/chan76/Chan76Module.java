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

package com.nttec.everychan.chans.chan76;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import com.nttec.everychan.R;
import com.nttec.everychan.api.AbstractVichanModule;
import com.nttec.everychan.api.interfaces.CancellableTask;
import com.nttec.everychan.api.interfaces.ProgressListener;
import com.nttec.everychan.api.models.BoardModel;
import com.nttec.everychan.api.models.SendPostModel;
import com.nttec.everychan.api.models.SimpleBoardModel;
import com.nttec.everychan.api.util.ChanModels;

public class Chan76Module extends AbstractVichanModule {
    private static final String CHAN_NAME = "76chan.org";
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "76", "76chan Discussion", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "br", "The Brothel", null, true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Spaghetti", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "International", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "Invasion", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fit", "Fitness", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "new", "Current Events", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r7k", "Robot 7600", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Aneemay", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sp", "Sports", null, false)
    };
    
    public Chan76Module(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return CHAN_NAME;
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_76chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_NAME;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return false;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 250;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
}
