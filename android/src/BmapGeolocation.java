package com.linkcld.cordova;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.linkcld.cordova.PermissionHelper;

/**
 * 百度定位cordova插件android端
 *
 * @author jack
 */
public class BmapGeolocation extends CordovaPlugin {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";

    /**
     * LOG TAG
     */
    private static final String LOG_TAG = BmapGeolocation.class.getSimpleName();
    
       /**
     * 安卓6以上动态权限相关
     */
    private static final int REQUEST_CODE = 100001;
    public static final int PERMISSION_DENIED_ERROR_CODE = 2;
    public static final String[] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };

    /**
     * JS回调接口对象
     */
    private CallbackContext cbCtx = null;
    private CallbackContext actionStartCallbackContext;

    /**
     * 百度定位客户端
     */
    public LocationClient mLocationClient = null;

    private LocationClientOption mOption;

    private BDLocation currentLocation;

    private ExecutorService executorService;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        executorService =  Executors.newSingleThreadExecutor();
    }

    protected Activity getActivity() {
        return this.cordova.getActivity();
    }

    protected Application getApplication() {
        return getActivity().getApplication();
    }

    protected Context getContext() {
        return getActivity().getApplicationContext();
    }

    protected void runOnUiThread(Runnable action) {
        getActivity().runOnUiThread(action);
    }

    /**
     * 百度定位监听
     */
    public BDLocationListener myListener = new BDLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation location) {
            currentLocation = location;
            try {
                JSONObject json = new JSONObject();

                json.put("time", location.getTime());
                json.put("locType", location.getLocType());
                json.put("locTypeDescription", location.getLocTypeDescription());
                json.put("latitude", location.getLatitude());
                json.put("longitude", location.getLongitude());
                json.put("radius", location.getRadius());

                json.put("countryCode", location.getCountryCode());
                json.put("country", location.getCountry());
                json.put("citycode", location.getCityCode());
                json.put("city", location.getCity());
                json.put("district", location.getDistrict());
                json.put("street", location.getStreet());
                json.put("addr", location.getAddrStr());
                json.put("province", location.getProvince());

                json.put("userIndoorState", location.getUserIndoorState());
                json.put("locationDescribe", location.getLocationDescribe());

                if (location.getLocType() == BDLocation.TypeGpsLocation){
                    
                    //当前为GPS定位结果，可获取以下信息
                    json.put("direction", location.getDirection());//获取方向信息，单位度
                    json.put("speed",location.getSpeed());//获取当前速度，单位：公里每小时
                    json.put("altitude", location.getAltitude()); //获取海拔高度信息，单位米
         
                }

                PluginResult pluginResult;
                if (location.getLocType() == BDLocation.TypeServerError
                        || location.getLocType() == BDLocation.TypeNetWorkException
                        || location.getLocType() == BDLocation.TypeCriteriaException) {

                    json.put("msg", "定位失败");
                    pluginResult = new PluginResult(PluginResult.Status.ERROR, json);
                    pluginResult.setKeepCallback(true);
                } else {
                    pluginResult = new PluginResult(PluginResult.Status.OK, json);
                    pluginResult.setKeepCallback(true);
                }

                cbCtx.sendPluginResult(pluginResult);
            } catch (JSONException e) {
                String errMsg = e.getMessage();
                LOG.e(LOG_TAG, errMsg, e);

                PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, errMsg);
                cbCtx.sendPluginResult(pluginResult);
            } 
        }

    };

    private boolean hasPermissions() {
        for(String p : permissions) {
            if(!PermissionHelper.hasPermission(this, p)) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        ArrayList<String> permissionsToRequire = new ArrayList<String>();

        for(String p : permissions) {
            if(!PermissionHelper.hasPermission(this, p)) {
                permissionsToRequire.add(p);
            }
        }

        String[] _permissionsToRequire = new String[permissionsToRequire.size()];
        _permissionsToRequire = permissionsToRequire.toArray(_permissionsToRequire);
        PermissionHelper.requestPermissions(this, REQUEST_CODE, _permissionsToRequire);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (cbCtx == null || requestCode != REQUEST_CODE)
            return;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                JSONObject json = new JSONObject();
                json.put("code", PERMISSION_DENIED_ERROR_CODE);
                json.put("msg", "定位失败");
                LOG.e(LOG_TAG, "权限请求被拒绝");
                actionStartCallbackContext.error(json);
                actionStartCallbackContext = null;
                return;
            }
        }
        switch (requestCode) {
            case REQUEST_CODE:
                actionStartCallbackContext.success();
                actionStartCallbackContext = null;
                break;
        }
        startLocationClient();
    }

    /**
     * 插件主入口
     */
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        
        if (ACTION_START.equalsIgnoreCase(action)) {
            executorService.execute(new Runnable() {
                public void run() {
                    if (mLocationClient == null) {
                        callbackContext.error("Plugin not configured. Please call configure method first.");
                        return;
                    }
                    if (!hasPermissions()) {
                        LOG.i(LOG_TAG,"Requesting permissions from user");
                        actionStartCallbackContext = callbackContext;
                        requestPermission();
                        return;
                    }
                    LOG.i(LOG_TAG,"start location client!-----");
                    startLocationClient();
                    callbackContext.success();
                }
            });
        } else if (ACTION_STOP.equalsIgnoreCase(action)) {
            executorService.execute(new Runnable() {
                public void run() {
                    stopLocationClient();
                    callbackContext.success();
                }
            });

            return true;
        } else if (ACTION_CONFIGURE.equalsIgnoreCase(action)) {
            this.cbCtx = callbackContext;
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        //config = Config.fromJSONObject(args.getJSONObject(0));
                        configueLocation();
                        // callbackContext.success(); //we cannot do this
                    //}  catch (JSONException e) {
                        //log.error("Configuration error: {}", e.getMessage());
                     //   callbackContext.error("Configuration error: " + e.getMessage());
                    } catch (NullPointerException e) {
                        LOG.e(LOG_TAG,"Configuration error: ", e);
                        callbackContext.error("Configuration error: " + e.getMessage());
                    }
                }
            });
            return true;
        } 

        return false;
    }


    /**
     * 权限获得完毕后进行定位
     */
    private void configueLocation() {
        if (mLocationClient == null) {
            mLocationClient = new LocationClient(this.getContext());
            mLocationClient.registerLocationListener(myListener);
            mLocationClient.setLocOption(getDefaultLocationClientOption());
        }
    }

    private void startLocationClient() {
        mLocationClient.start();
    }

    private void stopLocationClient() {
        mLocationClient.stop();
    }


    public LocationClientOption getDefaultLocationClientOption() {
        if (mOption == null) {
            mOption = new LocationClientOption();
            mOption.setLocationMode(LocationMode.Hight_Accuracy);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
            //mOption.setCoorType("bd09ll");可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
            mOption.setScanSpan(5000);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
            mOption.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
            mOption.setOpenGps(true); // 可选，默认false,设置是否使用gps            
            mOption.setNeedDeviceDirect(false);//可选，设置是否需要设备方向结果
            mOption.setLocationNotify(false);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
            mOption.setIgnoreKillProcess(true);//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
            mOption.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
            mOption.setIsNeedLocationPoiList(false);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
            mOption.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集

            mOption.setIsNeedAltitude(false);//可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用

        }
        return mOption;
    }
}
