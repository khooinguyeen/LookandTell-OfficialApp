package com.example.mainfinal;

import android.content.pm.ApplicationInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import com.example.mainfinal.R;
import com.google.common.util.concurrent.ListenableFuture;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.glutil.EglManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.mainfinal.ml.SignLangModel;

import org.jetbrains.annotations.NotNull;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Dichngu#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Dichngu extends Fragment implements Serializable {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String TAG = "Dichngu";
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String BINARY_GRAPH_NAME = "hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    private static final String INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands";
    private static final int NUM_HANDS = 2;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    // TODO: Rename and change types of parameters
    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    private String mParam1;
    private String mParam2;
    private SurfaceTexture previewFrameTexture;
    private SurfaceView previewDisplayView;
    private EglManager eglManager;
    private FrameProcessor processor;
    private ExternalTextureConverter converter;
    private ApplicationInfo applicationInfo;
    private CameraXPreviewHelper cameraHelper;
    private float[] result = new float[126];
    private float[][] sequence = new float[30][126];
    private float[][] outputArray = new float[30][126];
    protected Interpreter tflite;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private static final String MODEL_FILENAME = "file:///ml/SignLangModel.tflite";
    private MappedByteBuffer tfliteModel;
    String actualModelFilename = MODEL_FILENAME.split("file:///ml/", -1)[1];
    public Dichngu() {
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Dichngu.
     */
    // TODO: Rename and change types and number of parameters
    public static Dichngu newInstance(String param1, String param2) {
        Dichngu fragment = new Dichngu();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_dichngu, container, false); // no delete
        FrameLayout startRecordBtn = view.findViewById(R.id.startRecordBtn);

        previewDisplayView = new SurfaceView(view.getContext());
        setupPreviewDisplayView(view);
        AndroidAssetUtil.initializeNativeAssetManager(view.getContext());
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        getActivity(),
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(getActivity());
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();

        inputSidePackets.put(INPUT_NUM_HANDS_SIDE_PACKET_NAME, packetCreator.createInt32(NUM_HANDS));
        processor.setInputSidePackets(inputSidePackets);

        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    for(int i=0; i<30; i++) {
                        List<NormalizedLandmarkList> multiHandLandmarks =
                                PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());
                        result = extractHandLandmarks(multiHandLandmarks);
//                        Log.v(TAG, String.valueOf(result.length));
                        sequence[i] = result;
//                        Log.v(TAG, String.valueOf(sequence[i][0]));
//                        System.out.print(Arrays.toString(sequence[i]));
                    }
                    try {
                        tfliteModel = loadModelFile(getActivity().getAssets(), actualModelFilename);
                        tflite = new Interpreter(tfliteModel, tfliteOptions);
                        tflite.run(sequence, outputArray);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


//                    try {
//                        // TODO: only pick last 30 frames
////                        resultAsByteBuffer = toByteBuffer(result);
////                        Log.v(TAG, StandardCharsets.ISO_8859_1.decode(resultAsByteBuffer).toString());
////                        String converted = new String(resultAsByteBuffer.array());
////                        Log.v(TAG, converted);
////                        sequenceAsByteBuffer = ByteBuffer.allocate(sequenceAsByteBuffer.limit()+resultAsByteBuffer.limit()).put(resultAsByteBuffer);
//                        SignLangModel model = SignLangModel.newInstance(view.getContext());
//                        TensorBuffer inputLandmarks = TensorBuffer.createFixedSize(new int[]{1, 30, 126}, DataType.FLOAT32);
//
//                        inputLandmarks.loadArray(result);
//                        SignLangModel.Outputs outputs = model.process(inputLandmarks);
//                        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//                        TextView txtDichngu = view.findViewById(R.id.txtDichNgu);
//                        txtDichngu.setText(outputFeature0.toString());
//                        Log.v(TAG, String.valueOf(outputFeature0));
//                    } catch (IOException e){
//                        e.printStackTrace();
//                    }
//                     TODO: collect 30 frame, edit float[] result

                });


        if (startRecordBtn != null) {
            startRecordBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.equals(startRecordBtn)) {
                        Fragment fragment = new dichngu_stop();
                        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.main, fragment).commit();
                    }
                }
            });
        }

        return view;//no delete
    }

    @Override
    public void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(getActivity())) {
            startCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                getActivity(), cameraFacing, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
    }

    private void setupPreviewDisplayView(View view) {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = view.findViewById(R.id.preview);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }



    public float[] extractHandLandmarks(List<NormalizedLandmarkList> multiHandLandmarks) {
        List<Float> keypoints = new ArrayList<>();
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                keypoints.add(landmark.getX());
                keypoints.add(landmark.getY());
                keypoints.add(landmark.getZ());
            }
        }
        float[] result = new float[keypoints.size()];
        int i = 0;

        for (Float f : keypoints) {
            result[i++] = (f != null ? f : Float.NaN); // Or whatever default you want.
        }
        return result;
    }

    private ByteBuffer toByteBuffer(List<Float> list) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(list);
        byte[] bytes = bos.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(504); //15120 = 1*30*126*4, 504 = 126*4
        ByteBuffer.wrap(bytes);
        return buffer;
    }

    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

}
