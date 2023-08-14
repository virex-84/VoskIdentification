package com.virex.voiceident.ui.home;

import android.annotation.SuppressLint;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.virex.voiceident.common.AppExecutors;

import java.util.ArrayList;

public class SpkViewModel extends ViewModel {
    private final MutableLiveData<ArrayList<SpkUser>> users = new MutableLiveData<>();

    private static double sumSquares(Double[] data) {
        double ans = 0.0;
        for (int k = 0; k < data.length; k++) {
            ans += data[k] * data[k];
        }
        return (ans);
    }

    private static double norm(Double[] data) {
        return (Math.sqrt(sumSquares(data)));
    }

    private static double dot(Double[] a, Double[] b) throws ArithmeticException {
        if (a.length != b.length) {
            throw new ArithmeticException("The length of the vectors does not match!");
        } else {
            double sum = 0;
            for (int i = 0; i < a.length; i++) {
                sum += a[i] * b[i];
            }
            return sum;
        }

    }

    /*
    https://github.com/alphacep/vosk-api/blob/12f29a3415e4967e088ed09202bfb0007e5a1787/python/example/test_speaker.py#L53
    def cosine_dist(x, y):
        nx = np.array(x)
        ny = np.array(y)
        return 1 - np.dot(nx, ny) / np.linalg.norm(nx) / np.linalg.norm(ny)
    */
    private static double dist(Double[] a, Double[] b){
        return 1 - dot(a,b) / norm(a) / norm (b);
    }

    private SpkUser getDefault(ArrayList<SpkUser> items){
        for (SpkUser user : items){
            if (user.spk==null) return user;
        }
        SpkUser defaultSpkUser = new SpkUser("NotRecognized", null, 0.0, "", false);
        items.add(defaultSpkUser);
        return defaultSpkUser;
    }

    public void recognize(Double[] spk, Double spk_frames, String text){
        AppExecutors.getInstance().backgroundIO().execute(new Runnable() {

            @SuppressLint("DefaultLocale")
            @Override
            public void run() {
                ArrayList<SpkUser> items = users.getValue();

                //добавляем пользователя по умолчанию
                //для пометки неидентифицированного голоса
                if (items==null) {
                    items = new ArrayList<>();
                    getDefault(items);
                }

                //все молчат
                for (SpkUser user : items) {
                    user.isNowSpeak = false;
                }

                //не идентифицировали голос
                //либо слишком мало фреймов (spk_frames)
                //присваиваем текст пользователю по умолчанию
                //и выходим
                if (spk==null || spk.length==0 || spk_frames < 100){
                    SpkUser defaultSpkUser = getDefault(items);

                    defaultSpkUser.lastText = text;
                    defaultSpkUser.isNowSpeak = true;

                    users.postValue(items);
                    return;
                }

                //проверяем каждого пользователя
                for (SpkUser user : items) {

                    //defaultSpkUser игнорируем
                    if (user.spk==null) continue;

                    //рассчитываем дистанцию для каждого из голосов
                    //пояснения:
                    //чем больше дистанция
                    //тем меньше предыдущий голос похож на текущий
                    //пример:
                    //0.1 - максимально похожий голос
                    //0.9 - максимально НЕпохожий голос
                    double dist = dist(user.spk, spk);

                    //помечаем дистанцию
                    user.lastDist = dist;
                }

                //ищем среди пользователей с максимально похожим голосом (минимальная дистанция)
                double minDist = 100.0;
                SpkUser minUser = null;
                for (SpkUser user : items) {

                    //defaultSpkUser игнорируем
                    if (user.spk==null) continue;

                    if (minDist > user.lastDist) {
                        minDist = user.lastDist;
                        minUser = user;
                    }
                }

                //если у пользователя с максимальным похожим голосом
                //порог не превышает [значение] - считаем что его нашли
                if (minUser!=null && minUser.lastDist < 0.5 ){
                    minUser.lastSpkFrames = spk_frames;
                    minUser.lastText = text;
                    minUser.isNowSpeak = true;
                    users.postValue(items);
                    return;
                }

                //не нашли ни одного совпадения
                //добавляем новый голос
                items.add(new SpkUser(String.format("User%d",items.size()), spk, spk_frames, text, true));

                users.postValue(items);
            }
        });
    }


    public LiveData<ArrayList<SpkUser>> getRecognized(){
        return users;
    }

    public void clearUsers(){
        users.postValue(null);
    }
}
