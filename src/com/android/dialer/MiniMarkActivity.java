/*
 * Copyright (C) 2015 The MoKee OpenSource Project
 * Copyright (C) 2015 The SudaMod Project
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

package com.android.dialer;

import android.app.Activity;
import android.content.ContentValues;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.util.Log;

import com.sudamod.sdk.phonelocation.PhoneUtil;


public class MiniMarkActivity extends Activity implements AdapterView.OnItemClickListener {

    private TextView tvMarkNumber;
    private EditText btnMarkNumberEdit;
    private ListView lvMarkList;
    private String phoneNumber;
    private static Map<String,CallBack> mCallBackMap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mini_mark);
        lvMarkList = (ListView) findViewById(R.id.mark_list);
        tvMarkNumber = (TextView) findViewById(R.id.mark_number_header);
        btnMarkNumberEdit = (EditText) findViewById(R.id.mark_number_edit);
        lvMarkList.setOnItemClickListener(this);

        Bundle bundle = getIntent().getExtras();
        phoneNumber = bundle.getString("number");
        if (TextUtils.isEmpty(phoneNumber)) finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvMarkNumber.setText(String.format(getString(R.string.cloud_location_lookup_mark_title), phoneNumber));
    }

    public static void setCallBack(String clzName, CallBack callback){
        if(mCallBackMap == null)
            mCallBackMap = new HashMap<String, CallBack>();
        mCallBackMap.put(clzName, callback);
    }

    public void updateUserMarkInfo(String number, String userMark, int phoneType) {
        PhoneUtil.getPhoneUtil(this).customMark(number, userMark, phoneType);

        Log.d("mCallBackMap.size()", mCallBackMap.size()+"");
        Iterator<Map.Entry<String, CallBack>> iterator = mCallBackMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CallBack> entry = iterator.next();
            ((CallBack) entry.getValue()).updateView();
        }
        finish();
    }

    public void btnCancelOnClick(View view) {
        finish();
    }

    public void btnComfirmOnClick(View view) {
        String userMark = btnMarkNumberEdit.getText().toString().trim();
            updateUserMarkInfo(phoneNumber, userMark, 1);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int value[] = getResources().getIntArray(R.array.cloud_location_lookup_mark_values);
        updateUserMarkInfo(phoneNumber, parent.getItemAtPosition(position).toString(), value[position]);
    }

    public interface CallBack {
        public void updateView();
    }

}
