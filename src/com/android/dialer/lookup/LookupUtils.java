/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup;

import android.text.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.StringBuilder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LookupUtils {
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:26.0) Gecko/20100101 Firefox/26.0";

    public static String httpGet(String url, Map<String, String> headers) throws IOException {
        // open connection
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        // set user agent (default value is null)
        urlConnection.setRequestProperty("User-Agent", USER_AGENT);
        // set all other headers if not null
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }
        }
        // query url, read and return buffered response body
        // we want to make sure that the connection gets closed here
        StringBuilder stringBuilder = null;
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream()));
            stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line + "\n");
            }
            bufferedReader.close();
        } finally {
            urlConnection.disconnect();
        }
        return (stringBuilder == null) ? null : stringBuilder.toString();
    }

    public static String[] allRegexResults(String input, String regex, boolean dotall) {
        if (input == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex, dotall ? Pattern.DOTALL : 0);
        Matcher matcher = pattern.matcher(input);

        List<String> regexResults = new ArrayList<String>();
        while (matcher.find()) {
            regexResults.add(matcher.group(1).trim());
        }
        return regexResults.toArray(new String[regexResults.size()]);
    }

    public static String firstRegexResult(String input, String regex, boolean dotall) {
        if (input == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex, dotall ? Pattern.DOTALL : 0);
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1).trim() : null;
    }

    public static String fromHtml(String input) {
        if (input == null) {
            return null;
        }
        return Html.fromHtml(input).toString().trim();
    }
}
