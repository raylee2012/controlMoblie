package com.controlmoblie.service.binder;

import android.os.Binder;

import androidx.lifecycle.MutableLiveData;

import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.ScreenState;

public class AccessibilityBinder extends Binder {
    private final MutableLiveData<ScreenState> screenState = new MutableLiveData<>();
    private final MutableLiveData<InstructionResult> actionResult = new MutableLiveData<>();

    public void setScreenState(ScreenState state) {
        screenState.postValue(state);
    }

    public void setActionResult(InstructionResult result) {
        actionResult.postValue(result);
    }

    public MutableLiveData<ScreenState> getScreenState() {
        return screenState;
    }

    public MutableLiveData<InstructionResult> getActionResult() {
        return actionResult;
    }
}
