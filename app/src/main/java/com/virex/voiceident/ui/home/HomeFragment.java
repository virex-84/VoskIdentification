package com.virex.voiceident.ui.home;

import static androidx.core.content.ContextCompat.checkSelfPermission;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virex.voiceident.R;
import com.virex.voiceident.models.ModelInfo;
import com.virex.voiceident.common.StorageService;
import com.virex.voiceident.databinding.FragmentHomeBinding;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.SpeakerModel;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.IOException;
import java.util.ArrayList;

public class HomeFragment extends Fragment implements  RecognitionListener {

    boolean isProcess = false;
    private FragmentHomeBinding binding;

    public enum State {
        STATE_READY,
        STATE_STARTED
    }

    private Model model;
    private SpeakerModel modelSpk;
    private SpeechService speechService;

    SpkViewModel spkViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        spkViewModel = getDefaultViewModelProviderFactory().create(SpkViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> {
                if (result) {
                    //права выданы
                    initModel();
                } else {
                    //нет прав
                    if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.RECORD_AUDIO)) {
                        //пишем почему нужны эти права приложению
                        Snackbar.make(binding.getRoot(), R.string.Why_permissions_need, Snackbar.LENGTH_LONG).show();
                    } else {
                        //ругаемся что нет прав
                        Toast.makeText(requireContext(),R.string.Error_grand_permission,Toast.LENGTH_LONG);
                    }
                }

            });

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setUiState(State.STATE_READY);

        LibVosk.setLogLevel(LogLevel.DEBUG);

        binding.btnVoice.setOnClickListener(v -> {

            if (speechService != null) {
                recognizeMicrophone();
                return;
            }

            if (checkSelfPermission(requireActivity(), Manifest.permission.RECORD_AUDIO) != PermissionChecker.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                //права на микрофон уже выданы
                initModel();
            }
        });

        spkViewModel.getRecognized().observe(getViewLifecycleOwner(), new Observer<ArrayList<SpkUser>>() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onChanged(ArrayList<SpkUser> spkUsers) {
                if (spkUsers==null) {
                    binding.tvUsers.setText("");
                    return;
                }
                //отображаем список пользователей
                StringBuilder text = new StringBuilder();
                for(SpkUser user : spkUsers){
                    text.append(String.format("%s %4.3f (%4.3f)", user.name, user.lastDist, user.lastSpkFrames).concat("\n"));

                    //добавляем в историю того кто сейчас "сказал" текст
                    if (user.isNowSpeak)
                        binding.tvText.append(String.format("%s: %s", user.name, user.lastText).concat("\n"));
                }
                binding.tvUsers.setText(text.toString());
            }
        });
    }

    private void initModel() {
        //грузим модели из assets во внешнюю память
        //либо если всё в порядке - используем модели
        new StorageService(requireContext(),"info.json")
                .unpack("model-ru-ru", "models")
                .unpack("vosk-model-spk-0.4","models")
                .setCallback(new StorageService.Callback() {
                    @Override
                    public void onComplete(ArrayList<ModelInfo> models) {
                        try {
                            for(ModelInfo modelInfo : models){
                                if (modelInfo.type.equals("voice")) model    = new Model(modelInfo.absolutePath);
                                if (modelInfo.type.equals("spk"))   modelSpk = new SpeakerModel(modelInfo.absolutePath);
                            }
                            setUiState(State.STATE_STARTED);
                            recognizeMicrophone();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        binding.tvText.setText(exception.getMessage());
                    }
                })
                .process();
    }

    private void setUiState(State state) {
        switch (state) {
            case STATE_READY:
                isProcess = false;
                binding.btnVoice.setText(R.string.Ready);
                break;
            case STATE_STARTED:
                isProcess = true;
                binding.btnVoice.setText(R.string.Stop);
                break;
            default:
                ;//throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(State.STATE_READY);
            speechService.stop();
            speechService = null;

            //экран может выключаться
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            binding.tvCurrent.setText(R.string.Read_Complete);

        } else {
            setUiState(State.STATE_STARTED);
            try {

                Recognizer rec = new Recognizer(model, 16000.0f);
                rec.setSpeakerModel(modelSpk); //подключаем модель идентификации голоса
                //rec.setMaxAlternatives(0);
                //rec.setWords(true);

                //очищаем список пользователей
                spkViewModel.clearUsers();
                //очищаем историю
                binding.tvText.setText("");

                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);

                //экран не выключается
                requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            } catch (IOException e) {
                binding.tvText.setText(e.getMessage());
            }
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (!isProcess || binding==null) return;

        JsonObject jsonObject = JsonParser.parseString(hypothesis).getAsJsonObject();
        String text = jsonObject.get("partial").getAsString();

        if (TextUtils.isEmpty(text))
            binding.tvCurrent.setText(R.string.Wait_voice);
        else
            binding.tvCurrent.setText(text);
    }

    @Override
    public void onResult(String hypothesis) {
        if (!isProcess || binding==null) return;

        //вытаскиваем текст
        JsonObject jsonObject = JsonParser.parseString(hypothesis).getAsJsonObject();
        String text = jsonObject.get("text").getAsString();

        if (TextUtils.isEmpty(text)) return;

        //векторы ("отпечаток" пользователя)
        JsonElement spkString = jsonObject.get("spk");
        Double[] spk = null;
        if (spkString!=null) {
            Gson gson = new Gson();
            spk = gson.fromJson(spkString,Double[].class);
        }

        //количество фреймов
        JsonElement spkframesString = jsonObject.get("spk_frames");
        double spk_frames = 0;
        if (spkframesString!=null) spk_frames =  spkframesString.getAsDouble();

        //пушим текст и идентификационные данные в нашу вьюмодель
        spkViewModel.recognize(spk, spk_frames, text);

        //binding.tvText.append(text + "\n");
    }

    @Override
    public void onFinalResult(String hypothesis) {
    }

    @Override
    public void onError(Exception exception) {
        if (!isProcess || binding==null) return;

        Snackbar.make(binding.getRoot(), R.string.Recognize_error, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onTimeout() {
    }
}