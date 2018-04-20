/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.ArImage;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.examples.java.helloar.ws.Detect;
import com.google.ar.core.examples.java.helloar.price.PropertyPriceLookup;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();
  public ArrayList<ARProperty> listObjects = new ArrayList<>();

  public static HelloArActivity INSTANCE;
  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private TapHelper tapHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];

  // Anchors created from taps used for object placing.
  private final ArrayList<Anchor> anchors = new ArrayList<>();

  private PropertyPriceLookup propertyPriceLookup = new PropertyPriceLookup();
  private BigDecimal propertyPriceTotal = new BigDecimal(0);
  private List<ARProperty> allSelectedPropertyData = new ArrayList<ARProperty>();
  private Boolean canPlaceModel = Boolean.TRUE;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    INSTANCE = this;
    setContentView(R.layout.activity_helloar);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up tap listener.
    tapHelper = new TapHelper(/*context=*/ this);
    surfaceView.setOnTouchListener(tapHelper);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;
  }

  public void grabItems(View view) {
    setContentView(R.layout.activity_display_table);
  }

  public void grabItems2(View view) {
    setContentView(R.layout.activity_helloar);
    this.recreate();
  }

  public void goToDisplayTable(View view) {
    setContentView(R.layout.activity_display_table);
    addRowToTable();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);

      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();

    messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(/*context=*/ this);
      planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(/*context=*/ this);

      virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
      virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      virtualObjectShadow.createOnGlThread(
          /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);
    boolean capturePicture = false;
    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Handle taps. Handling only one tap per frame, as taps are usually low frequency
      // compared to frame rate.

      MotionEvent tap = tapHelper.poll();
      if (tap != null && camera.getTrackingState() == TrackingState.TRACKING && canPlaceModel) {
        for (HitResult hit : frame.hitTest(tap)) {
          // Check if any plane was hit, and if it was hit inside the plane polygon
          Trackable trackable = hit.getTrackable();
          // Creates an anchor if a plane or an oriented point was hit.
          if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
              || (trackable instanceof Point
                  && ((Point) trackable).getOrientationMode()
                      == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
            // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
            // Cap the number of objects created. This avoids overloading both the
            // rendering system and ARCore.
            if (anchors.size() >= 20) {
              anchors.get(0).detach();
              anchors.remove(0);
            }

            // Adding an Anchor tells ARCore that it should track this position in
            // space. This anchor is created on the Plane to place the 3D model
            // in the correct position relative both to the world and to the plane.
            anchors.add(hit.createAnchor());
            capturePicture = true;
            makeButtonsVisible();
            break;
          }
        }
      }

      // Draw background.
      backgroundRenderer.draw(frame);

      if (capturePicture) {
        capturePicture = false;

        ArImage image = (ArImage)frame.acquireCameraImage();
        System.out.println("format="+image.getFormat());
        System.out.println("planes="+image.getPlanes());
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] byteArray = new byte[buffer.capacity()];
        buffer.get(byteArray);
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

//U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] byteArray2 = out.toByteArray();

        /*Image image = frame.acquireCameraImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] byteArray = new byte[buffer.capacity()];
        buffer.get(byteArray);
        image.close();
        Bitmap bm = yuv2RBG(image.getWidth(), image.getHeight(), byteArray);*/

        /*ByteArrayOutputStream bstream = new ByteArrayOutputStream();
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.blender);
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,bstream);
        byte[] byteArray = bstream.toByteArray();*/


        Context context = this.getApplicationContext();
        String response = new Detect().getResponse(context,byteArray2);
        JSONArray r = Detect.parseJSON(response);
        System.out.println(r.toString());

        ARProperty item = new ARProperty();
        item.cost = BigDecimal.valueOf(0.0);
        item.propertyDescription = "fdsnfkljdshkj";
        listObjects.add(item);
      }

      // If not tracking, don't draw 3d objects.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      pointCloudRenderer.update(pointCloud);
      pointCloudRenderer.draw(viewmtx, projmtx);

      // Application is responsible for releasing the point cloud resources after
      // using it.
      pointCloud.release();

      // Check if we detected at least one plane. If so, hide the loading message.
      if (messageSnackbarHelper.isShowing()) {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
          if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
              && plane.getTrackingState() == TrackingState.TRACKING) {
            messageSnackbarHelper.hide(this);
            break;
          }
        }
      }

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // Visualize anchors created by touch.
      float scaleFactor = 1.0f;
      for (Anchor anchor : anchors) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        anchor.getPose().toMatrix(anchorMatrix, 0);

        // Update and draw the model and its shadow.
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba);
        virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba);
      }

    } catch (Throwable t) {
      capturePicture = false;
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

  public void addRowToTable(){

    TableLayout t1;
    TableLayout tl = (TableLayout) findViewById(R.id.main_table);

    TableRow tr_head = new TableRow(this);

    addColumn(tr_head, "ITEM");
    addColumn(tr_head, "VALUE");
    //addColumn(tr_head, "IMAGE FILE LOCATION");

    tl.addView(tr_head, new TableLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

    String propDesc;
    String cost;
    //String fileLocation;

    TableRow tr = new TableRow(this);

    for (int i = 0; i < listObjects.size(); i++){
      propDesc = listObjects.get(i).propertyDescription;
      cost = listObjects.get(i).cost.toString();
      //fileLocation = listObjects.get(i).fileLocation;

      addColumn(tr_head, propDesc);
      addColumn(tr_head, cost);
      //addColumn(tr_head, fileLocation);

      tl.addView(tr, new TableLayout.LayoutParams(
              ViewGroup.LayoutParams.FILL_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT));

    }
  }

  public void addColumn(TableRow tr, String displayText){
    TextView label_text = new TextView(this);
    label_text.setText(displayText);
    label_text.setTextColor(Color.WHITE);
    label_text.setPadding(5, 5, 5, 5);
    tr.addView(label_text);
  }

  //This is all bad!!!
  public void propertyOneSelected(View view) {
    handlePropertySelected(findViewById(R.id.propertyOne));
  }
  public void propertyTwoSelected(View view) {
    handlePropertySelected(findViewById(R.id.propertyTwo));
  }
  public void propertyThreeSelected(View view) {
    handlePropertySelected(findViewById(R.id.propertyThree));
  }
  public void propertyOtherSelected(View view) {
    handlePropertySelected(findViewById(R.id.propertyOther));
  }
  private void handlePropertySelected(Button selectedButton){
    BigDecimal itemPrice=updatePriceData();
    String itemName = selectedButton.getText().toString();
    createARPropertyAndAddToList(itemPrice,itemName);
    makeButtonsInvisible();
    canPlaceModel = true;
  }
  private void makeButtonsInvisible(){
    Button buttonOne=(Button) findViewById(R.id.propertyOne);
    Button buttonTwo=(Button) findViewById(R.id.propertyTwo);
    Button buttonThree=(Button) findViewById(R.id.propertyThree);
    Button buttonOther=(Button) findViewById(R.id.propertyOther);
/*    buttonOne.setVisibility(View.INVISIBLE);
    buttonTwo.setVisibility(View.INVISIBLE);
    buttonThree.setVisibility(View.INVISIBLE);
    buttonOther.setVisibility(View.INVISIBLE);*/
    buttonOne.setText("");
    buttonTwo.setText("");
    buttonThree.setText("");
    buttonOther.setText("");
  }
  private void makeButtonsVisible(){
    Button buttonOne=(Button) findViewById(R.id.propertyOne);
    Button buttonTwo=(Button) findViewById(R.id.propertyTwo);
    Button buttonThree=(Button) findViewById(R.id.propertyThree);
    Button buttonOther=(Button) findViewById(R.id.propertyOther);
/*    buttonOne.setVisibility(View.VISIBLE);
    buttonTwo.setVisibility(View.VISIBLE);
    buttonThree.setVisibility(View.VISIBLE);
    buttonOther.setVisibility(View.VISIBLE);*/
    buttonOne.setText("test1");
    buttonTwo.setText("test1");
    buttonThree.setText("test1");
    buttonOther.setText("test1");
    canPlaceModel = false;
  }

  /**
   *return the item cost
   */
  private BigDecimal updatePriceData(){
    BigDecimal itemPrice = propertyPriceLookup.getPrice();
    propertyPriceTotal = propertyPriceTotal.add(itemPrice);
    TextView propertyPriceText = (TextView) findViewById(R.id.propertyPrice);
    TextView propertyPriceTotalText = (TextView) findViewById(R.id.propertyPriceTotal);
    propertyPriceText.setText("+ "+itemPrice.toString());
    propertyPriceTotalText.setText(propertyPriceTotal.toString());
    return itemPrice;
  }
  private void createARPropertyAndAddToList(BigDecimal itemCost, String itemDescription){
    ARProperty prop = new ARProperty();
    prop.cost = itemCost;
    prop.propertyDescription = itemDescription;
    allSelectedPropertyData.add(prop);
  }


  private static Bitmap yuv2RBG(int imageWidth, int imageHeight, byte[] data) {
    // the bitmap we want to fill with the image
    Bitmap bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
    int numPixels = imageWidth*imageHeight;

// the buffer we fill up which we then fill the bitmap with
    IntBuffer intBuffer = IntBuffer.allocate(imageWidth*imageHeight);
// If you're reusing a buffer, next line imperative to refill from the start,
// if not good practice
    intBuffer.position(0);

// Set the alpha for the image: 0 is transparent, 255 fully opaque
    final byte alpha = (byte) 255;

// Get each pixel, one at a time
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        // Get the Y value, stored in the first block of data
        // The logical "AND 0xff" is needed to deal with the signed issue
        int Y = data[y*imageWidth + x] & 0xff;

        // Get U and V values, stored after Y values, one per 2x2 block
        // of pixels, interleaved. Prepare them as floats with correct range
        // ready for calculation later.
        int xby2 = x/2;
        int yby2 = y/2;

        // make this V for NV12/420SP
        float U = (float)(data[numPixels + 2*xby2 + yby2*imageWidth] & 0xff) - 128.0f;

        // make this U for NV12/420SP
        float V = (float)(data[numPixels + 2*xby2 + 1 + yby2*imageWidth] & 0xff) - 128.0f;

        // Do the YUV -> RGB conversion
        float Yf = 1.164f*((float)Y) - 16.0f;
        int R = (int)(Yf + 1.596f*V);
        int G = (int)(Yf - 0.813f*V - 0.391f*U);
        int B = (int)(Yf            + 2.018f*U);

        // Clip rgb values to 0-255
        R = R < 0 ? 0 : R > 255 ? 255 : R;
        G = G < 0 ? 0 : G > 255 ? 255 : G;
        B = B < 0 ? 0 : B > 255 ? 255 : B;

        // Put that pixel in the buffer
        intBuffer.put(alpha*16777216 + R*65536 + G*256 + B);
      }
    }

// Get buffer ready to be read
    intBuffer.flip();

// Push the pixel information from the buffer onto the bitmap.
    bitmap.copyPixelsFromBuffer(intBuffer);
    return bitmap;
  }
}
