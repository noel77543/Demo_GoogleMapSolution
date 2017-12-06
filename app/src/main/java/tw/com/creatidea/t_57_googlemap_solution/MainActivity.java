package tw.com.creatidea.t_57_googlemap_solution;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.support.v4.widget.DrawerLayout;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.kml.KmlLayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;
import tw.com.creatidea.t_57_googlemap_solution.basic.BasicMapActivity;
import tw.com.creatidea.t_57_googlemap_solution.connect.GoogleConnect;
import tw.com.creatidea.t_57_googlemap_solution.model.AddressInfo;
import tw.com.creatidea.t_57_googlemap_solution.model.DirectionInfo;
import tw.com.creatidea.t_57_googlemap_solution.model.PlaceInfo;
import tw.com.creatidea.t_57_googlemap_solution.navigation.NavigationDrawer;
import tw.com.creatidea.t_57_googlemap_solution.navigation.model.NavigationData;

import static tw.com.creatidea.t_57_googlemap_solution.util.EventCenter.TYPE_ADDRESS;
import static tw.com.creatidea.t_57_googlemap_solution.util.EventCenter.TYPE_DIRECTION;
import static tw.com.creatidea.t_57_googlemap_solution.util.EventCenter.TYPE_LOCATION;
import static tw.com.creatidea.t_57_googlemap_solution.util.EventCenter.TYPE_PLACE;

/**
 * Created by noel on 2017/12/5.
 */

public class MainActivity extends BasicMapActivity implements GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMapClickListener, NavigationDrawer.OnNavigationItemClickListener, View.OnKeyListener {


    //點選的目標點位
    private LatLng latLngTarget;
    private Marker markerTarget;
    private MarkerOptions optionsTarget = new MarkerOptions();

    private GoogleConnect connect;
    //用來控制infowindow 開啟或關閉
    private boolean isInfoWindowShown = false;
    //路線規劃
    private Polyline polyline;
    //drawer
    private NavigationDrawer navigationDrawer;

    // butterknife
    @BindView(R.id.edit)
    MultiAutoCompleteTextView edit;
    @BindView(R.id.btnFocus)
    Button btnFocus;
    @BindView(R.id.btnReload)
    Button btnReload;
    @BindView(R.id.listview)
    public ListView listview;
    @BindView(R.id.drawer_layout)
    public DrawerLayout drawerLayout;

    @Override
    protected int getContentViewId() {
        return R.layout.activity_main;
    }

    @Override
    protected void init() {
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edit.setOnKeyListener(this);
        initGooglemapUtils();
        navigationDrawer = new NavigationDrawer(this);
        navigationDrawer.setOnNavigationItemClickListener(this);
        connect = new GoogleConnect(this);
    }
    //----------

    /**
     * google map 地圖設定
     */
    private void initGooglemapUtils() {
        //隱藏準心按鈕
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setInfoWindowAdapter(new MyInfoAdapter(this));//資訊視窗樣式
        googleMap.setOnInfoWindowClickListener(this);// 資訊視窗點擊監聽
        googleMap.setOnMapClickListener(this);
    }

    //----------
    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    //----------
    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    //----------
    //EventBus
    @Subscribe
    public void onSuccessConnect(Map<String, Object> data) {
        //經緯度轉地址
        if ((int) data.get("type") == TYPE_ADDRESS) {
            AddressInfo addressInfo = (AddressInfo) data.get("data");
            addTargetMarkerOnMap(latLngTarget, getAddressFormat(addressInfo.getResults()));

            //輸入查詢 地址轉經緯度
        } else if ((int) data.get("type") == TYPE_LOCATION) {
            latLngTarget = (LatLng) data.get("data");
            addTargetMarkerOnMap((LatLng) data.get("data"), edit.getText().toString());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngTarget, 12.0f));

            //途經點查詢 路線規劃
        } else if ((int) data.get("type") == TYPE_DIRECTION) {
            DirectionInfo directionInfo = (DirectionInfo) data.get("data");
            addPolyLineOnMap(directionInfo);

            //地方資訊
        } else if ((int) data.get("type") == TYPE_PLACE) {
            PlaceInfo placeInfo = (PlaceInfo) data.get("data");
            new PlaceMarkerHandler(this, googleMap, placeInfo).execute();
            //reeor message
        } else {
            Toast.makeText(this, (String) data.get("data"), Toast.LENGTH_SHORT).show();
        }
    }
    //-----------------------------

    /**
     * 可能回傳 多筆地址資訊 有字串就return
     */
    private String getAddressFormat(List<AddressInfo.ResultsBean> results) {
        for (int i = 0; i < results.size(); i++) {
            String address = results.get(i).getFormattedAddress();
            if (address != null && !address.equals(""))
                return address;
        }
        return getString(R.string.text_googlemap_non_address);
    }

    //-----------------------------
    private void addTargetMarkerOnMap(LatLng myPostion, String myTitle) {

        if (markerTarget != null) {
            markerTarget.remove();
        }

        markerTarget = googleMap.addMarker(optionsTarget
                .position((myPostion))
                .title(myTitle)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.myicon))
                .anchor(0.5f, 0.9f));
        markerTarget.showInfoWindow();
    }

    //-----------------------------

    /**
     * 繪製polyline在地圖上
     */
    private void addPolyLineOnMap(DirectionInfo directionInfo) {
        if (polyline != null) {
            polyline.remove();
        }
        PolylineOptions polyLines = new PolylineOptions();
        polyLines.width(10);
        polyLines.color(Color.RED);
        String line = directionInfo.getRoutes().get(0).getOverview_polyline().getPoints();
        List<LatLng> decodedPath = PolyUtil.decode(line);
        polyline = googleMap.addPolyline(polyLines.addAll(decodedPath));
    }
    //----------

    /***
     *
     * 當選擇側邊欄項目時 取得附近五百公尺內該項目位置
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onNavigationItemClick(AdapterView<?> parent, View view, int position, long id) {
        googleMap.clear();
        NavigationData navigation = (NavigationData) parent.getAdapter().getItem(position);
        connect.connectToGetPlace(getUserLocation().getLatitude() + "", getUserLocation().getLongitude() + "", navigation.getTextEn());
    }
    //----------


    @Override
    public void onInfoWindowClick(final Marker marker) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage(MessageFormat.format(getString(R.string.toast_route), marker.getTitle()));
        dialog.setPositiveButton(getString(R.string.permission_goahead), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String startLat = getUserLocation().getLatitude() + "";
                String startLng = getUserLocation().getLongitude() + "";
                String endLat = marker.getPosition().latitude + "";
                String endLng = marker.getPosition().longitude + "";
                connect.connectToGetDirection(startLat, startLng, endLat, endLng);
            }
        });
        dialog.show();
    }

    //----------

    @Override
    public void onMapClick(LatLng latLng) {
        //每次繪製前這裡清除前一次繪製的路線
        if (polyline != null) {
            polyline.remove();
        }

        if (markerTarget != null && isInfoWindowShown) {
            markerTarget.hideInfoWindow();
            markerTarget.remove();
            isInfoWindowShown = false;
        } else if (!isInfoWindowShown) {

            latLngTarget = latLng;
            connect.connectToGetAddress(latLngTarget.latitude, latLngTarget.longitude);
            isInfoWindowShown = true;
        }
    }

    //--------
    // 進行 地址搜尋
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
            String searchAddress = edit.getText().toString();
            if (searchAddress.length() > 0) {
                connect.connectToGetLocation(searchAddress);
            } else {
                Toast.makeText(this, getString(R.string.toast_enteraddress), Toast.LENGTH_SHORT).show();
            }
            closeKeyboard();//關閉Keyboard
            return true;
        }
        return false;
    }


    //----------
    @OnClick({R.id.btnFocus, R.id.btnReload})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btnFocus:
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(getUserLocation().getLatitude(), getUserLocation().getLongitude()), 16.0f));
                break;

            case R.id.btnReload:
                checkPermissionsForResult();
                break;
        }
    }

    //-----------------------------

    /**
     * 更多Kml相關用法在以下網址
     * http://googlemaps.github.io/android-maps-utils/
     * https://developers.google.com/maps/documentation/android-api/utility/kml?hl=zh-tw
     */

    private void loadKML() {
        try {
            KmlLayer layer = new KmlLayer(googleMap, getAssets().open("testroute.kml"), this);
            layer.addLayerToMap();
            //            layer.removeLayerFromMap();移除該kmllayer
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
