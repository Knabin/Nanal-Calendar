package com.android.nanal.datetimepicker;

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * Fake Button class, used so TextViews can announce themselves as Buttons, for accessibility.
 * TextView가 접근성을 위해 버튼으로 자신을 알릴 수 있도록 사용되는 가짜 버튼 클래스
 *
 * @deprecated This module is deprecated. Do not use this class.
 */
@Deprecated
public class AccessibleTextView extends AppCompatTextView {

    public AccessibleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(Button.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
    }
}
