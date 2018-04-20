package com.amfam.wombat.arpropertypricecalculator.ws;

/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Base64;

import com.fasterxml.jackson.core.JsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesRequest;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Block;
import com.google.cloud.vision.v1.ColorInfo;
import com.google.cloud.vision.v1.CropHint;
import com.google.cloud.vision.v1.CropHintsAnnotation;
import com.google.cloud.vision.v1.DominantColorsAnnotation;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageContext;
import com.google.cloud.vision.v1.ImageSource;
import com.google.cloud.vision.v1.LocationInfo;
import com.google.cloud.vision.v1.Page;
import com.google.cloud.vision.v1.Paragraph;
import com.google.cloud.vision.v1.SafeSearchAnnotation;
import com.google.cloud.vision.v1.Symbol;
import com.google.cloud.vision.v1.TextAnnotation;
import com.google.cloud.vision.v1.WebDetection;
import com.google.cloud.vision.v1.WebDetection.WebEntity;
import com.google.cloud.vision.v1.WebDetection.WebImage;
import com.google.cloud.vision.v1.WebDetection.WebLabel;
import com.google.cloud.vision.v1.WebDetection.WebPage;
import com.google.cloud.vision.v1.WebDetectionParams;
import com.google.cloud.vision.v1.Word;

import com.google.protobuf.ByteString;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

import static android.app.PendingIntent.getActivity;



public class Detect {
    String visResponse;
    /**
     * Detects labels in the specified local image.
     *
     * @param filePath The path to the file to perform label detection on.
     * @param out A {@link PrintStream} to write detected labels to.
     * @throws Exception on errors while closing the client.
     * @throws IOException on Input/Output errors.
     */
    public static void detectLabels(String filePath, PrintStream out) throws Exception, IOException {
        List<AnnotateImageRequest> requests = new ArrayList<>();

        ByteString imgBytes = ByteString.readFrom(new FileInputStream(filePath));

        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Type.LABEL_DETECTION).build();
        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    out.printf("Error: %s\n", res.getError().getMessage());
                    return;
                }

                // For full list of available annotations, see http://g.co/cloud/vision/docs
                for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                    annotation.getAllFields().forEach((k, v) -> out.printf("%s : %s\n", k, v.toString()));
                }
            }
        }
    }

    /**
     * Detects labels in the specified remote image on Google Cloud Storage.
     *
     * @param gcsPath The path to the remote file on Google Cloud Storage to perform label detection
     *                on.
     * @param out A {@link PrintStream} to write detected features to.
     * @throws Exception on errors while closing the client.
     * @throws IOException on Input/Output errors.
     */
    public static void detectLabelsGcs(String gcsPath, PrintStream out) throws Exception,
            IOException {
        List<AnnotateImageRequest> requests = new ArrayList<>();

        ImageSource imgSource = ImageSource.newBuilder().setGcsImageUri(gcsPath).build();
        Image img = Image.newBuilder().setSource(imgSource).build();
        Feature feat = Feature.newBuilder().setType(Type.LABEL_DETECTION).build();
        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    out.printf("Error: %s\n", res.getError().getMessage());
                    return;
                }

                // For full list of available annotations, see http://g.co/cloud/vision/docs
                for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                    annotation.getAllFields().forEach((k, v) ->
                            out.printf("%s : %s\n", k, v.toString()));
                }
            }
        }
    }


    public static Image buildImage(String filePath) throws Exception {
        ByteString imgBytes = ByteString.readFrom(new FileInputStream(filePath));

        Image img = Image.newBuilder().setContent(imgBytes).build();

        return img;
    }

    public String getResponse(Context context,byte[] byteArray) {
        visResponse = new String();
        try {

            String requestURL = "https://vision.googleapis.com/v1/images:annotate?key=AIzaSyDL2EgkAvpP1tVvqCdn1HaedgyPG-t621c";
            AsyncHttpClient client = new AsyncHttpClient();
            StringEntity entity = new StringEntity(Detect.buildRequestJSON(byteArray));
            client.post(context, requestURL, entity, "application/json",new AsyncHttpResponseHandler(){
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                            visResponse = responseBody.toString();
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                            visResponse = responseBody.toString();
                        }
                    }

            );
        } catch(Exception e){

        }
        System.out.println(visResponse);
        return visResponse;
    }







    public static String buildRequestJSON(byte[] byteArray) {
        String requestJSON = "";
        String base64data = Base64.encodeToString(byteArray, Base64.URL_SAFE);

        try {
            // Create an array containing
            //  the LABEL_DETECTION feature
            JSONArray features = new JSONArray();
            JSONObject feature = new JSONObject();
            feature.put("type", "LABEL_DETECTION");
            features.put(feature);

            // Create an object containing
            // the Base64-encoded image data
            JSONObject imageContent = new JSONObject();
            imageContent.put("content", base64data);

            // Put the array and object into a single request
            // and then put the request into an array of requests
            JSONArray requests = new JSONArray();
            JSONObject request = new JSONObject();
            request.put("image", imageContent);
            request.put("features", features);
            requests.put(request);
            JSONObject postData = new JSONObject();
            postData.put("requests", requests);

            // Convert the JSON into a string
            requestJSON = postData.toString();
        } catch (Exception e) {
            System.err.print(e.getMessage());
        }
        return requestJSON;
    }


    public static List<EntityAnnotation> getTopThree(List<EntityAnnotation> annotations) {
        List<EntityAnnotation> choices = new ArrayList<>();

        int i = 0;
        int max = 3;
        while (i < annotations.size() && i < max){
            choices.add(annotations.get(i));
            i++;
        }

        EntityAnnotation other = EntityAnnotation.newBuilder().setDescription("Other").build();
        choices.add(other);
        return choices;
    }
}

