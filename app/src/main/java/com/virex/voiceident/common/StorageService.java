package com.virex.voiceident.common;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.ArrayMap;
import android.util.Log;

import com.google.gson.Gson;
import com.virex.voiceident.models.ModelInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/*
Класс для синхронизации моделей assets -> externalstorage
 */
public class StorageService {
    protected static final String TAG = org.vosk.android.StorageService.class.getSimpleName();

    public interface Callback {
        void onComplete(ArrayList<ModelInfo> models);
        void onError(Exception exception);
    }
    private final ArrayMap<String,String> items = new ArrayMap<>();
    private Callback callback;
    private final Context context;
    private final String modelJson;

    public StorageService(Context context, String modelJson){
        this.context = context;
        this.modelJson = modelJson;
    }

    public StorageService setCallback(Callback callback){
        this.callback = callback;
        return this;
    }

    public StorageService unpack(String sourcePath, final String targetPath){
        items.put(sourcePath,targetPath);
        return this;
    }

    public void process(){
        AppExecutors.getInstance().mainThread().execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<ModelInfo> models = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    String sourcePath = items.keyAt(i);
                    String targetPath = items.valueAt(i);
                    try {
                        //синхронизируем (нет папки или guid различается - (пере)распаковываем, есть папка - возвращаем информацию)
                        final ModelInfo modelInfo = sync(sourcePath, targetPath);
                        models.add(modelInfo);
                    } catch (final IOException e) {
                        if (callback!=null) callback.onError(e);
                        return;
                    }
                }
                if (callback!=null) callback.onComplete(models);
            }
        });
    }

    private ModelInfo sync(String sourcePath, String targetPath) throws IOException {
        AssetManager assetManager = context.getAssets();

        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            throw new IOException("cannot get external files dir, "
                    + "external storage state is " + Environment.getExternalStorageState());
        }

        File targetDir = new File(externalFilesDir, targetPath);

        ModelInfo sourceModelInfo = getModelInfo(assetManager.open(sourcePath + "/" + this.modelJson));
        //обязательно указываем полный путь
        sourceModelInfo.absolutePath=targetDir.getAbsolutePath()+'/'+sourcePath;
        try {
            //если файл уже есть - не перезаписываем
            ModelInfo targetModelInfo = getModelInfo(new FileInputStream(new File(targetDir, sourcePath + "/" + this.modelJson)));
            if (sourceModelInfo.uuid.equals(targetModelInfo.uuid)) return sourceModelInfo;
        } catch (FileNotFoundException ignore) {}

        //файла нет - создаем/перезаписываем
        deleteContents(new File(sourceModelInfo.absolutePath));

        copyAssets(assetManager, sourcePath, targetDir);

        return sourceModelInfo;
    }

    private static ModelInfo getModelInfo(InputStream is) throws IOException {
        try(Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, ModelInfo.class);
        }
    }

    private static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }

    private static void copyAssets(AssetManager assetManager, String path, File outPath) throws IOException {
        String[] assets = assetManager.list(path);
        if (assets == null) {
            return;
        }
        if (assets.length == 0) {
            //файл
            copyFile(assetManager, path, outPath);
        } else {
            //папка
            File dir = new File(outPath, path);
            if (!dir.exists()) {
                Log.v(TAG, "Making directory " + dir.getAbsolutePath());
                if (!dir.mkdirs()) {
                    Log.v(TAG, "Failed to create directory " + dir.getAbsolutePath());
                }
            }
            for (String asset : assets) {
                copyAssets(assetManager, path + "/" + asset, outPath);
            }
        }
    }

    private static void copyFile(AssetManager assetManager, String fileName, File outPath) throws IOException {
        Log.v(TAG, "Copy " + fileName + " to " + outPath);

        try(InputStream in = assetManager.open(fileName);
            //Files.newOutputStream выдает ошибку на Android 5.0 (API 21)
            OutputStream out = new FileOutputStream(outPath + "/" + fileName)){

            byte[] buffer = new byte[4000];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
