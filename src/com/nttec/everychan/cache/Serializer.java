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

package com.nttec.everychan.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang3.tuple.Pair;

import com.nttec.everychan.api.models.AttachmentModel;
import com.nttec.everychan.api.models.BadgeIconModel;
import com.nttec.everychan.api.models.BoardModel;
import com.nttec.everychan.api.models.DeletePostModel;
import com.nttec.everychan.api.models.PostModel;
import com.nttec.everychan.api.models.SendPostModel;
import com.nttec.everychan.api.models.SimpleBoardModel;
import com.nttec.everychan.api.models.ThreadModel;
import com.nttec.everychan.api.models.UrlPageModel;
import com.nttec.everychan.common.Async;
import com.nttec.everychan.common.IOUtils;
import com.nttec.everychan.common.Logger;
import com.nttec.everychan.common.MainApplication;
import com.nttec.everychan.lib.KryoOutputHC;
import com.nttec.everychan.ui.tabs.TabModel;
import com.nttec.everychan.ui.tabs.TabsIdStack;
import com.nttec.everychan.ui.tabs.TabsState;
import android.os.Build;
import android.support.v4.util.AtomicFile;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer;

/**
 * Сериализация объектов (на основе kryo)
 * @author miku-nyan
 *
 */
public class Serializer {
    private static final String TAG = "Serializer";
    
    private final FileCache fileCache;
    private final AtomicFile tabsStateFile;
    private final Kryo kryo;
    private final Object kryoLock = new Object();
    
    /**
     * Конструктор
     * @param fileCache объект файлового кэша
     */
    public Serializer(FileCache fileCache) {
        this.fileCache = fileCache;
        this.tabsStateFile = new AtomicFile(new File(fileCache.getFilesDirectory(), FileCache.TABS_FILENAME));
        
        this.kryo = new Kryo();
        this.kryo.setReferences(false);
        this.kryo.setDefaultSerializer(TaggedFieldSerializer.class);
        
        this.kryo.register(TabsState.class, 0);
        this.kryo.register(TabModel.class, 1);
        this.kryo.register(TabsIdStack.class, 2);
        
        this.kryo.register(SerializablePage.class, 3);
        this.kryo.register(SerializableBoardsList.class, 4);
        
        this.kryo.register(AttachmentModel.class, 5);
        this.kryo.register(BadgeIconModel.class, 6);
        this.kryo.register(BoardModel.class, 7);
        this.kryo.register(DeletePostModel.class, 8);
        this.kryo.register(PostModel.class, 9);
        this.kryo.register(SendPostModel.class, 10);
        this.kryo.register(SimpleBoardModel.class, 11);
        this.kryo.register(ThreadModel.class, 12);
        this.kryo.register(UrlPageModel.class, 13);
        
        this.kryo.register(AttachmentModel[].class, 14);
        this.kryo.register(BadgeIconModel[].class, 15);
        this.kryo.register(BoardModel[].class, 16);
        this.kryo.register(DeletePostModel[].class, 17);
        this.kryo.register(PostModel[].class, 18);
        this.kryo.register(SendPostModel[].class, 19);
        this.kryo.register(SimpleBoardModel[].class, 20);
        this.kryo.register(ThreadModel[].class, 21);
        this.kryo.register(UrlPageModel[].class, 22);
        
        this.kryo.register(java.util.ArrayList.class, 23);
        this.kryo.register(java.util.LinkedList.class, 24);
        this.kryo.register(java.io.File.class, new FileSerializer(), 25);
        this.kryo.register(java.io.File[].class, 26);
    }
    
    private void serialize(String filename, Object obj) {
        synchronized (kryoLock) {
            File file = fileCache.create(filename);
            Output output = null;
            try {
                output = createOutput(new FileOutputStream(file));
                kryo.writeObject(output, obj);
            } catch (Exception e) {
                Logger.e(TAG, e);
            } catch (OutOfMemoryError oom) {
                MainApplication.freeMemory();
                Logger.e(TAG, oom);
            } finally {
                IOUtils.closeQuietly(output);
            }
            fileCache.put(file);
        }
    }
    
    private void serializeAsync(final String filename, final Object obj) {
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                serialize(filename, obj);
            }
        });
    }
    
    private <T> T deserialize(File file, Class<T> type) {
        if (file == null || !file.exists()) {
            return null;
        }
        
        synchronized (kryoLock) {
            Input input = null;
            try {
                input = new Input(new FileInputStream(file));
                return kryo.readObject(input, type);
            } catch (Exception e) {
                Logger.e(TAG, e);
            } catch (OutOfMemoryError oom) {
                MainApplication.freeMemory();
                Logger.e(TAG, oom);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
        
        return null;
    }
    
    public <T> T deserialize(String fileName, Class<T> type) {
        return deserialize(fileCache.get(fileName), type);
    }
    
    public void serializePage(String hash, SerializablePage page) {
        serializeAsync(FileCache.PREFIX_PAGES + hash, page);
    }
    
    public SerializablePage deserializePage(String hash) {
        try {
            return deserialize(FileCache.PREFIX_PAGES + hash, SerializablePage.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void serializeBoardsList(String hash, SerializableBoardsList boardsList) {
        serializeAsync(FileCache.PREFIX_BOARDS + hash, boardsList);
    }
    
    public SerializableBoardsList deserializeBoardsList(String hash) {
        try {
            return deserialize(FileCache.PREFIX_BOARDS + hash, SerializableBoardsList.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void serializeDraft(String hash, SendPostModel draft) {
        serializeAsync(FileCache.PREFIX_DRAFTS + hash, draft);
    }
    
    public SendPostModel deserializeDraft(String hash) {
        try {
            return deserialize(FileCache.PREFIX_DRAFTS + hash, SendPostModel.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void removeDraft(String hash) {
        File file = fileCache.get(FileCache.PREFIX_DRAFTS + hash);
        if (file != null) {
            fileCache.delete(file);
        }
    }
    
    public void serializeTabsState(final TabsState state) {
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                synchronized (kryoLock) {
                    FileOutputStream fileStream = null;
                    try {
                        fileStream = tabsStateFile.startWrite();
                        Output output = createOutput(fileStream);
                        kryo.writeObject(output, state);
                        output.flush();
                        tabsStateFile.finishWrite(fileStream);
                    } catch (Exception|OutOfMemoryError e) {
                        if (e instanceof OutOfMemoryError) MainApplication.freeMemory();
                        Logger.e(TAG, e);
                        tabsStateFile.failWrite(fileStream);
                    }
                }
            }
        });
    }
    
    public TabsState deserializeTabsState() {
        synchronized (kryoLock) {
            Input input = null;
            try {
                input = new Input(tabsStateFile.openRead());
                TabsState obj = kryo.readObject(input, TabsState.class);
                if (obj != null && obj.tabsArray != null && obj.tabsIdStack != null) return obj;
            } catch (Exception e) {
                Logger.e(TAG, e);
            } catch (OutOfMemoryError e) {
                MainApplication.freeMemory();
                Logger.e(TAG, e);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
        return TabsState.obtainDefault();
    }
    
    public void savePage(OutputStream out, String title, UrlPageModel pageModel, SerializablePage page) {
        synchronized (kryoLock) {
            Output output = null;
            try {
                output = createOutput(out);
                output.writeString(title);
                kryo.writeObject(output, pageModel);
                kryo.writeObject(output, page);
            } finally {
                IOUtils.closeQuietly(output);
            }
        }
    }
    
    public Pair<String, UrlPageModel> loadPageInfo(InputStream in) {
        synchronized (kryoLock) {
            Input input = null;
            try {
                input = new Input(in);
                String title = input.readString();
                UrlPageModel pageModel = kryo.readObject(input, UrlPageModel.class);
                return Pair.of(title, pageModel);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }
    
    public SerializablePage loadPage(InputStream in) {
        synchronized (kryoLock) {
            Input input = null;
            try {
                input = new Input(in);
                input.readString();
                kryo.readObject(input, UrlPageModel.class);
                return kryo.readObject(input, SerializablePage.class);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }
    
    private class FileSerializer extends com.esotericsoftware.kryo.Serializer<java.io.File> {
        @Override
        public void write (Kryo kryo, Output output, File object) {
            output.writeString(object.getPath());
        }
        @Override
        public File read (Kryo kryo, Input input, Class<File> type) {
            return new File(input.readString());
        }
    }
    
    private static Output createOutput(OutputStream stream) {
        return isHoneycomb() ? new KryoOutputHC(stream) : new Output(stream);
    }
    
    private static boolean isHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2;
    }
}
