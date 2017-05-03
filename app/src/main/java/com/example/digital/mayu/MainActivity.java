package com.example.digital.mayu;

import android.content.Context;
import android.graphics.Paint;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.baidu.mapapi.SDKInitializer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMap.SnapshotReadyCallback;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    public Button btnstop;      // go on - pause
    public String string = "status: ";
    public TextView status;
    public TextView tvshow;
    public SensorManager sensorManager;
    public Sensor ssrlin;
    public boolean st = false;      // st == true --> work， the left button
    public boolean ps = false;      // ps == true ---> pause, the right button
    public float xacc = 0;
    public float yacc = 0;
    public float zacc = 0;
    public double b = 0;
    public float c = (Math.round(0*1000))/1000;

    /********   graph   ******      drawing     ************/

    private Timer timer = new Timer();
    private TimerTask task;
    private Handler handler;
    private String title = "Signal Strength";
    private XYSeries series;
    private XYMultipleSeriesDataset mDataset;
    private GraphicalView chart;
    private XYMultipleSeriesRenderer renderer;
    private Context context;
    private int addX = -1, addY;

    int[] xv = new int[100];
    int[] yv = new int[100];

    /********   GPS     ********/
    // 定位相关
    LocationClient mLocClient;
    public MyLocationListenner myListener = new MyLocationListenner();
    // UI相关
    MapView mMapView;
    BaiduMap mBaiduMap;

    boolean isFirstLoc = true;              // 是否首次定位
    boolean isRecording = false;            // 是否在记录
    boolean isFirstRecord = true;           // 是否首次记录
    private LatLng local;           	    // 定位模式下设置当前位置
    //记录模式下设置当前定位位置和上一次定位位置
    private LatLng nowlocal = null;         // 当前
    private LatLng lastlocal = null;        // 上一次
    private double distance = 0.0;          // 总距离
    //截图路径
    private File file = new File(Environment.getExternalStorageDirectory() + File.separator + "recordShot.png");
    private TextView distanceView;      	// 设置显示路程View
    //设置记录按钮
    private Button btnStartRecord,btnStopRecord,btnClear,btnScreenShot,btnScreenShotShare;
    private static final int UPDATE_TIME = 1000;        	// 设置定位间隔时间(Ms)
    //设置Handler消息
    private static final int DISTANCE_UNEQUAL_ZERO = 1;
    private static final int DISTANCE_EQUAL_ZERO = 0;
    @SuppressLint("HandlerLeak")
    private Handler Handler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case DISTANCE_UNEQUAL_ZERO:
                    distanceView.setText("我走了"+(int)distance+"米");
                    break;
                case DISTANCE_EQUAL_ZERO:
                    distanceView.setText("我没动呢～");
                default:
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext()); //  我居然是忘了这句话！！！！
        setContentView(R.layout.activity_main);

        tvshow = (TextView) findViewById(R.id.textView);
        status = (TextView) findViewById(R.id.status);
        btnstop = (Button) findViewById(R.id.stop);

        /********   graph   *******/

        context = getApplicationContext();

        //这里获得main界面上的布局，下面会把图表画在这个布局里面
        LinearLayout layout = (LinearLayout)findViewById(R.id.linearLayout1);

        //这个类用来放置曲线上的所有点，是一个点的集合，根据这些点画出曲线
        series = new XYSeries(title);

        //创建一个数据集的实例，这个数据集将被用来创建图表
        mDataset = new XYMultipleSeriesDataset();

        //将点集添加到这个数据集中
        mDataset.addSeries(series);

        //以下都是曲线的样式和属性等等的设置，renderer相当于一个用来给图表做渲染的句柄
        int color = Color.GREEN;
        PointStyle style = PointStyle.CIRCLE;
        renderer = buildRenderer(color, style, true);

        //设置好图表的样式
        setChartSettings(renderer, "X", "Y", 0, 100, 0, 30, Color.WHITE, Color.WHITE);

        //生成图表
        chart = ChartFactory.getLineChartView(context, mDataset, renderer);

        //将图表添加到布局中去
        layout.addView(chart, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        //这里的Handler实例将配合下面的Timer实例，完成定时更新图表的功能
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                //刷新图表
                updateChart();
                super.handleMessage(msg);
            }
        };
        task = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                handler.sendMessage(message);
            }
        };
        timer.schedule(task, 500, 500);




        /***********     GPS       ***********/
        mMapView = (MapView) findViewById(R.id.bmapView);    // 地图初始化
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMyLocationEnabled(true);   	// 开启定位图层
        mLocClient = new LocationClient(this);          // 定位初始化
        mLocClient.registerLocationListener(myListener);

        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);             // 打开GPS
        option.setCoorType("bd09ll");        // 设置坐标类型
        option.setScanSpan(UPDATE_TIME);     // 定位时间间隔
        mLocClient.setLocOption(option);
        mLocClient.start();
        mMapView.refreshDrawableState();     // refresh

        distanceView = (TextView) findViewById(R.id.distanceView);
        distanceView.setVisibility(View.GONE);
        distanceView.setTextColor(Color.GREEN);
        distanceView.setTextSize(25);

        // gps button define
        btnStartRecord = (Button) findViewById(R.id.btnStartRecord);
        btnStopRecord = (Button) findViewById(R.id.btnStopRecord);
        btnClear = (Button) findViewById(R.id.btnClear);
        btnScreenShot = (Button) findViewById(R.id.btnScreenShot);
        btnScreenShotShare = (Button) findViewById(R.id.btnScreenShotShare);
        btnStopRecord.setVisibility(View.GONE);
        btnClear.setVisibility(View.GONE);
        btnScreenShot.setVisibility(View.GONE);
        btnScreenShotShare.setVisibility(View.GONE);

        // use gps button
        btnStartRecord.setOnClickListener(onClickListener);
        btnStopRecord.setOnClickListener(onClickListener);
        btnClear.setOnClickListener(onClickListener);
        btnScreenShot.setOnClickListener(onClickListener);
        btnScreenShotShare.setOnClickListener(onClickListener);
    }


    @Override
    protected void onStart() {
        super.onStart();
        string += "onstart ";
        status.setText(string);
    }
    @Override
    protected void onResume(){
        super.onResume();
        string += "onresume ";
        status.setText(string);
    }

    /***************     Running   part  start     ****************/

    /**********     Button      *********/
    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(v.equals(btnstop))
                ps = !ps;
                // gps button
            else if (v.equals(btnStartRecord)) {
                // sensor on
                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                ssrlin = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);   // 注册
                sensorManager.registerListener(sensorEventListener,ssrlin,SensorManager.SENSOR_DELAY_NORMAL);
                string += "is_on ";
                status.setText(string);
                // gps on
                status.setText(string);
                isRecording = true;
                btnstop.setVisibility(View.VISIBLE);
                distanceView.setVisibility(View.VISIBLE);
                btnStartRecord.setVisibility(View.GONE);
                btnStopRecord.setVisibility(View.VISIBLE);
                btnClear.setVisibility(View.GONE);
                btnScreenShot.setVisibility(View.GONE);
                btnScreenShotShare.setVisibility(View.GONE);
            }else if (v.equals(btnStopRecord)) {
                // sensor off
                sensorManager.unregisterListener(sensorEventListener);
                sensorManager = null;   // 解除监听器注册
                string += "is_off ";
                tvshow.setText("linear acceleration: \n x acc:0\n y acc:0\n z acc:0");
                // gps off
                isRecording = false;
                isFirstRecord = true;
                btnStopRecord.setVisibility(View.GONE);
                btnStartRecord.setVisibility(View.VISIBLE);
                btnClear.setVisibility(View.VISIBLE);
                btnScreenShot.setVisibility(View.VISIBLE);
                btnScreenShotShare.setVisibility(View.GONE);
                btnstop.setVisibility(View.GONE);
            }else if (v.equals(btnClear)) {
                clearRecord();
                isRecording = false;
                isFirstRecord = true;
                distanceView.setVisibility(View.GONE);
                btnClear.setVisibility(View.GONE);
                btnScreenShot.setVisibility(View.GONE);
                btnScreenShotShare.setVisibility(View.GONE);
                distance = 0.0;
                Handler.sendEmptyMessage(DISTANCE_EQUAL_ZERO);
            }else if (v.equals(btnScreenShot)) {
                btnScreenShot.setVisibility(View.GONE);
                btnScreenShotShare.setVisibility(View.VISIBLE);

                //截取轨迹图并保存到SD卡
                mBaiduMap.snapshot(new SnapshotReadyCallback() {
                    @SuppressLint("SdCardPath")
                    @Override
                    public void onSnapshotReady(Bitmap snapshot) {

                        FileOutputStream out;
                        try {
                            out = new FileOutputStream(file);
                            if (snapshot.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                out.flush();
                                out.close();
                            }
                            Toast.makeText(MainActivity.this, "截图成功，图片保存在：" + file.toString(),
                                    Toast.LENGTH_SHORT).show();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                Toast.makeText(MainActivity.this, "正在截取图片...", Toast.LENGTH_SHORT).show();
            }
            else if (v.equals(btnScreenShotShare)) {
                btnScreenShotShare.setVisibility(View.GONE);
                btnScreenShot.setVisibility(View.VISIBLE);
                shareMsg(file.toString());     			//分享截图
            }
        }
    };



    /**********     Sensor      ***********/
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event_lin) {

            btnstop.setOnClickListener(onClickListener);      //  continue / pause
            if(event_lin.sensor == null)    return;
            if(event_lin.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                if(!ps){
                    // "!ps" means "working"
                    xacc = (float) (Math.round(event_lin.values[0]*1000))/1000;
                    yacc = (float) (Math.round(event_lin.values[1]*1000))/1000;
                    zacc = (float) (Math.round(event_lin.values[2]*1000))/1000;
                    String x = "x acc:" + xacc + "\n";
                    String y = "y acc:" + yacc + "\n";
                    String z = "z acc:" + zacc + "\n";
                    b = Math.sqrt(xacc*xacc + yacc*yacc + zacc*zacc);
                    String dd = Math.abs((float) (Math.round((b-c)*1000))/1000) +"\n";
                    String s = "linear acceleration: \n" + x + y + z + "上一次：" + c + "\n";
                    c = (float) (Math.round(b*1000))/1000;
                    String d = c + "\n";
                    tvshow.setText(s + "这一次：" + d + "\n" + "平稳度："+ dd);

                    // Store data
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");   // data format
                    String str = Environment.getExternalStorageDirectory() + File.separator + "Group6.txt";
                    File file = new File(str);              //  create a new file
                    String date = sdf.format(new Date());   //  record time
                    try{
                        if(!file.exists()){
                            file.createNewFile();
                        }
                        FileWriter fw = new FileWriter(file,true);
                        fw.write(date + "\n" + x + y + z + d + "\n");
                        fw.flush();
                        fw.close();
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor,int accuracy) {
        }
    };


    /***********    graph   *************/

    protected XYMultipleSeriesRenderer buildRenderer(int color, PointStyle style, boolean fill) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        //设置图表中曲线本身的样式，包括颜色、点的大小以及线的粗细等
        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(color);
        r.setPointStyle(style);
        r.setFillPoints(fill);
        r.setLineWidth(3);
        renderer.addSeriesRenderer(r);

        return renderer;
    }

    protected void setChartSettings(XYMultipleSeriesRenderer renderer, String xTitle, String yTitle,
                                    double xMin, double xMax, double yMin, double yMax, int axesColor, int labelsColor) {
        //有关对图表的渲染可参看api文档
        renderer.setChartTitle(title);
        renderer.setXTitle(xTitle);
        renderer.setYTitle(yTitle);
        renderer.setXAxisMin(xMin);
        renderer.setXAxisMax(xMax);
        renderer.setYAxisMin(yMin);
        renderer.setYAxisMax(yMax);
        renderer.setAxesColor(axesColor);
        renderer.setLabelsColor(labelsColor);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.GREEN);
        renderer.setXLabels(20);
        renderer.setYLabels(10);
        renderer.setXTitle("Sta");
        renderer.setYTitle("dBm");
        renderer.setYLabelsAlign(Paint.Align.RIGHT);
        renderer.setPointSize((float) 2);
        renderer.setShowLegend(false);
    }


    private void updateChart() {
        //设置好下一个需要增加的节点
        addX = 0;
        addY = (int) b;
//        addY = (int)(Math.random() * 90);

        //移除数据集中旧的点集
        mDataset.removeSeries(series);

        //判断当前点集中到底有多少点，因为屏幕总共只能容纳100个，所以当点数超过100时，长度永远是100
        int length = series.getItemCount();
        if (length > 100) {
            length = 100;
        }

        //将旧的点集中x和y的数值取出来放入backup中，并且将x的值加1，造成曲线向右平移的效果
        for (int i = 0; i < length; i++) {
            xv[i] = (int) series.getX(i) + 1;
            yv[i] = (int) series.getY(i);
        }

        //点集先清空，为了做成新的点集而准备
        series.clear();
        //将新产生的点首先加入到点集中，然后在循环体中将坐标变换后的一系列点都重新加入到点集中
        //这里可以试验一下把顺序颠倒过来是什么效果，即先运行循环体，再添加新产生的点
        series.add(addX, addY);
        for (int k = 0; k < length; k++) {
            series.add(xv[k], yv[k]);
        }

        //在数据集中添加新的点集
        mDataset.addSeries(series);

        //视图更新，没有这一步，曲线不会呈现动态
        //如果在非UI主线程中，需要调用postInvalidate()，具体参考api
        chart.invalidate();
    }


    /*******    GPS     *********/

    /***** 	   分享功能, 图片路径，不分享图片则传null     ****/
    public void shareMsg(String imgPath) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        if (imgPath == null || imgPath.equals("")) {
            intent.setType("text/plain"); // 纯文本
        } else {
            File f = new File(imgPath);
            if (f != null && f.exists() && f.isFile()) {
                intent.setType("image/*");
                Uri u = Uri.fromFile(f);
                intent.putExtra(Intent.EXTRA_STREAM, u);
            }
        }
        intent.putExtra(Intent.EXTRA_TEXT, "我已经走了" + distance + "米");
        startActivity(Intent.createChooser(intent, "分享到"));
    }

    /******	    清除轨迹记录		******/
    public void clearRecord() {
        mBaiduMap.clear();  		// 清除所有图层
    }

    /******* 	定位SDK监听函数	******/
    public class MyLocationListenner implements BDLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不再处理新接收的位置
            if (location == null || mMapView == null)
                return;
            //第一次定位时
            if (isFirstLoc) {
                isFirstLoc = false;
                LatLng firstlocal = new LatLng(location.getLatitude(),
                        location.getLongitude());
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(firstlocal);
                mBaiduMap.animateMapStatus(u); //将当前位置显示到地图中心
            }
            //非第一次定位

            MyLocationData locData = new MyLocationData.Builder()//如果不显示定位精度圈，将accuracy赋值为0即可
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(100).latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            mBaiduMap.setMyLocationData(locData);       //更新定位数据
            mMapView.refreshDrawableState();
            local = new LatLng(location.getLatitude(),location.getLongitude());	//获取当前位置的经纬度

            //按下记录按钮后记录轨迹并计算距离
            if (isRecording && !ps) {
                if (isFirstRecord) {
                    lastlocal = local;
                    isFirstRecord = false;
                }
                nowlocal = local;   	//当前位置赋予nowlocal
                getRecord();            //求距离并画线
                lastlocal = nowlocal;
            }
        }
        @Override
        public void onConnectHotSpotMessage(String s, int i) {
        }

        //记录轨迹并计算距离函数
        private void getRecord(){
            //  测距并计算总距离
            distance += DistanceUtil.getDistance(nowlocal, lastlocal);
            Handler.sendEmptyMessage(DISTANCE_UNEQUAL_ZERO);
            //  画线
            List<LatLng> points = new ArrayList<LatLng>();
            points.add(nowlocal);
            points.add(lastlocal);
            OverlayOptions polyline = new PolylineOptions().width(4).color(0xAAFF0000).points(points);
            mBaiduMap.addOverlay(polyline);
        }
        public void onReceivePoi(BDLocation poiLocation) {
        }
    }

    /****************＊     Running   part  off     *******************/






    @Override
    protected void onPause() {
        string += "onpause ";
        status.setText(string);
        mMapView.onPause();
        super.onPause();
    }
    @Override
    protected void onStop() {
        mMapView.onResume();
        string += "onstop ";
        status.setText(string);
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
            sensorManager = null;   // 解除监听器注册
            string += "is_off ";
        }
        // graph
        //当结束程序时关掉Timer
        timer.cancel();

        // gps
        mLocClient.stop();		// 退出时销毁定位
        mBaiduMap.setMyLocationEnabled(false);    // 关闭定位图层
        mMapView.onDestroy();
        mMapView = null;

        string += "ondestroy";
        status.setText(string);
        super.onDestroy();
    }

}
