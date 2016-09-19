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

package com.nttec.everychan.ui;

import java.util.ArrayList;
import java.util.List;

import com.nttec.everychan.api.ChanModule;
import com.nttec.everychan.common.Logger;
import com.nttec.everychan.common.MainApplication;
import com.nttec.everychan.lib.org_json.JSONArray;
import com.nttec.everychan.lib.org_json.JSONObject;

public class QuickAccess {
    private static final String TAG = "QuickAccess";
    
    private QuickAccess() {}
    
    public static class Entry {
        public ChanModule chan;
        public String boardName;
        public String boardDescription;
    }
    
    private static List<Entry> getQuickAccessListFromJson(String json) {
        boolean showAllChansList = MainApplication.getInstance().settings.showAllChansList();
        try {
            List<Entry> result = new ArrayList<>();
            int allChansElCount = 0;
            JSONArray jsonArray = new JSONArray(json);
            for (int i=0, len=jsonArray.length(); i<len; ++i) {
                Entry current = new Entry();
                JSONObject currentJson = jsonArray.getJSONObject(i);
                String chan = currentJson.optString("chan", null);
                if (chan != null) {
                    current.chan = MainApplication.getInstance().getChanModule(chan);
                    String board = currentJson.optString("board", null);
                    if (board != null) {
                        current.boardName = board;
                        current.boardDescription = currentJson.optString("description", null);
                    }
                } else {
                    ++allChansElCount;
                    if (!showAllChansList) continue;
                }
                result.add(current);
            }
            if (allChansElCount == 1) return result;
            throw new Exception("invalid json of quick access list: "+allChansElCount+" title elements");
        } catch (Exception e) {
            Logger.e(TAG, e);
            List<Entry> result = new ArrayList<>();
            if (showAllChansList) result.add(new Entry());
            return result;
        }
    }
    
    private static String saveQuickAccessListToJson(List<Entry> list) {
        JSONArray jsonArray = new JSONArray();
        int allChansElCount = 0;
        for (Entry entry : list) {
            JSONObject current = new JSONObject();
            if (entry.chan == null) {
                ++allChansElCount;
            } else {
                current.put("chan", entry.chan.getChanName());
                if (entry.boardName != null) current.put("board", entry.boardName);
                if (entry.boardDescription != null) current.put("description", entry.boardDescription);
            }
            jsonArray.put(current);
        }
        if (allChansElCount == 0) jsonArray.put(new JSONObject());
        return jsonArray.toString();
    }
    
    public static List<Entry> getQuickAccessFromPreferences() {
        return getQuickAccessListFromJson(MainApplication.getInstance().settings.getQuickAccessListJson());
    }
    
    public static void saveQuickAccessToPreferences(List<Entry> list) {
        MainApplication.getInstance().settings.saveQuickAccessListJson(saveQuickAccessListToJson(list));
    }
}
