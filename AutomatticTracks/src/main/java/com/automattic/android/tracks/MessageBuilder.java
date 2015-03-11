package com.automattic.android.tracks;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

class MessageBuilder {

    private static final String USER_INFO_PREFIX = "user_info_";
    private static final String DEVICE_INFO_PREFIX = "device_info_";

    private static final String EVENT_NAME_KEY = "_en";
    private static final String USER_AGENT_NAME_KEY = "_via_ua";
    private static final String TIMESTAMP_KEY = "_ts";
    private static final String USER_TYPE_KEY = "_ut";
    private static final String USER_TYPE_ANON= "anon";
    private static final String USER_ID_KEY = "_ui";
    private static final String USER_LOGIN_NAME_KEY = "_ul";

    public static final String ALIAS_USER_EVENT_NAME = "_aliasUser";

    public static synchronized boolean isReservedKeyword(String keyToTest) {
        String keyToTestLowercase = keyToTest.toLowerCase();
        if (keyToTestLowercase.equals(EVENT_NAME_KEY) ||
                keyToTestLowercase.equals(USER_AGENT_NAME_KEY) ||
                keyToTestLowercase.equals(TIMESTAMP_KEY) ||
                keyToTestLowercase.equals(USER_TYPE_KEY) ||
                keyToTestLowercase.equals(USER_ID_KEY) ||
                keyToTestLowercase.equals(USER_LOGIN_NAME_KEY)
                ) {
            return true;
        }

        if (keyToTestLowercase.startsWith(USER_INFO_PREFIX) ||
                keyToTestLowercase.startsWith(DEVICE_INFO_PREFIX)) {
            return true;
        }

        return false;
    }

    public static synchronized JSONObject createRequestCommonPropsJSONObject(DeviceInformation deviceInformation,
                                                                             JSONObject userProperties) {
        JSONObject commonProps = new JSONObject();
        unfolderProperties(deviceInformation.getImmutableDeviceInfo(), DEVICE_INFO_PREFIX, commonProps);
        unfolderProperties(deviceInformation.getMutableDeviceInfo(), DEVICE_INFO_PREFIX, commonProps);
        unfolderProperties(userProperties, USER_INFO_PREFIX, commonProps);
        return commonProps;
    }

    public static synchronized JSONObject createEventJSONObject(Event event, JSONObject commonProps) {
        try {
            JSONObject eventJSON = new JSONObject();
            eventJSON.put(EVENT_NAME_KEY, event.getEventName());

            eventJSON.put(USER_AGENT_NAME_KEY, event.getUserAgent());
            eventJSON.put(TIMESTAMP_KEY, event.getTimeStamp());

            if (event.getUserType() == TracksClient.NosaraUserType.ANON) {
                eventJSON.put(USER_TYPE_KEY, USER_TYPE_ANON);
                eventJSON.put(USER_ID_KEY, event.getUser());
            } else {
                eventJSON.put(USER_LOGIN_NAME_KEY, event.getUser());
                // no need to put the user type key here. default wpcom is used on the server.
            }

            unfolderPropertiesNotAvailableInCommon(event.getUserProperties(), USER_INFO_PREFIX, eventJSON, commonProps);
            unfolderPropertiesNotAvailableInCommon(event.getDeviceInfo(), DEVICE_INFO_PREFIX, eventJSON, commonProps);
            unfolderProperties(event.getCustomEventProperties(), "", eventJSON);

            // FIXME: Property names should also be lowercase and use underscores instead of dashes
            // but for a particular event/prop this is not the case
            if (event.getEventName().equals(ALIAS_USER_EVENT_NAME)) {
                String anonID = eventJSON.getString("anonid");
                eventJSON.put("anonId", anonID);
                eventJSON.remove("anonid");
            }

           return eventJSON;
        } catch (JSONException err) {
            Log.e(TracksClient.LOGTAG, "Cannot write the JSON representation of this object", err);
            return null;
        }
    }

    // Nosara only strings property values. Don't convert JSON objs by calling toString()
    // otherwise they will be likely un-queryable
    private static void unfolderPropertiesNotAvailableInCommon(JSONObject objectToFlatten, String flattenPrefix,
                                                                 JSONObject targetJSONObject, JSONObject commonProps) {
        if (objectToFlatten == null || targetJSONObject == null) {
            return;
        }

        if (flattenPrefix == null) {
            Log.w(TracksClient.LOGTAG, " Unfolding props with an empty key. Make sure the keys are unique!");
            flattenPrefix = "";
        }

        Iterator<String> iter = objectToFlatten.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            String flattenKey = String.valueOf(flattenPrefix + key).toLowerCase();
            try {
                Object value = objectToFlatten.get(key);
                String valueString;
                if (value != null) {
                    valueString = String.valueOf(value);
                } else {
                    valueString = "";
                }

                String valueStringInCommons = null;
                // Check if the same key/value is already available in common props
                if (commonProps != null && commonProps.has(flattenKey)) {
                    Object valueInCommons = commonProps.get(flattenKey);
                    if (valueInCommons != null) {
                        valueStringInCommons = String.valueOf(valueInCommons);
                    }
                }

                // Add the value at event level only if it's different from common
                if (valueStringInCommons == null || !valueStringInCommons.equals(valueString)) {
                    targetJSONObject.put(flattenKey, valueString);
                }
            } catch (JSONException e) {
                // Something went wrong!
                Log.e(TracksClient.LOGTAG, "Cannot write the flatten JSON representation of this object", e);
            }
        }
    }

    // Nosara only strings property values. Don't convert JSON objs by calling toString()
    // otherwise they will be likely un-queryable
    private static void unfolderProperties(JSONObject objectToFlatten, String flattenPrefix, JSONObject targetJSONObject) {
        unfolderPropertiesNotAvailableInCommon(objectToFlatten, flattenPrefix, targetJSONObject, null);
    }
}