package com.szabit.androidproject;

import java.util.List;

public interface IOnLoadListener {
    void onLoadLocationSuccess(List<MyLatLng> latLngs);
    void onLoadLocationFailed(String message);
}
