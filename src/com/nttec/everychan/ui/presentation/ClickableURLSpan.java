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

package com.nttec.everychan.ui.presentation;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.view.View;

public class ClickableURLSpan extends ClickableSpan {
    public static interface URLSpanClickListener {
        public void onClick(View v, ClickableURLSpan span, String url, String referer);
    }
    
    private final String url;
    private String referer;
    private URLSpanClickListener listener;
    
    public ClickableURLSpan(String url) {
        this.url = url;
    }
    
    @Override
    public void onClick(View widget) {
        if (listener != null) {
            listener.onClick(widget, this, url, referer);
        }
    }
    
    public ClickableURLSpan setOnClickListener(URLSpanClickListener listener) {
        this.listener = listener;
        return this;
    }
    
    public ClickableURLSpan setReferer(String referer) {
        this.referer = referer;
        return this;
    }
    
    public String getURL() {
        return url;
    }
    
    public static ClickableURLSpan replaceURLSpan(SpannableStringBuilder builder, URLSpan span, int color) {
        int start = builder.getSpanStart(span);
        int end = builder.getSpanEnd(span);
        String url = span.getURL();
        
        builder.removeSpan(span);
        
        ClickableURLSpan newSpan = new ClickableURLSpan(url);
        builder.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        return newSpan;
    }
}
