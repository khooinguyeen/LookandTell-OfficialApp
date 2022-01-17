package com.example.mainfinal;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import com.example.mainfinal.R;
import com.google.common.util.concurrent.ListenableFuture;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.fragment.app.Fragment;

import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

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
    private float[][] sequence = new float[60][126];
    private float[][] labelProbArray = null;
    private float[][] filterLabelProbArray = null;
    protected Interpreter tflite;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private List<String> labelList;
    private static final int FILTER_STAGES = 1; //3
    private static final float FILTER_FACTOR = 0.4f;
    private static final int RESULTS_TO_SHOW = 1; //3
    private static final float GOOD_PROB_THRESHOLD = 0.3f;
    private static final int SMALL_COLOR = 0xffddaa88;
    private SpannableStringBuilder builder;
    private TextView txtDichngu;
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

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
        View view = inflater.inflate(R.layout.fragment_dichngu, container, false); // no delete
        FrameLayout startRecordBtn = view.findViewById(R.id.startRecordBtn);
        txtDichngu = view.findViewById(R.id.txtDichNgu);
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

        try {
            initializeModel();
            labelList = loadLabelList();
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize model.", e);
        }

        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    printTopKLabels(builder);
                    for(int i=0; i<60; i++) {
                        List<NormalizedLandmarkList> multiHandLandmarks =
                                PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());
                        result = extractHandLandmarks(multiHandLandmarks);
                        sequence[i] = result;
                    }
                    filterLabelProbArray = new float[FILTER_STAGES][getNumLabels()];
                    labelProbArray = new float[1][getNumLabels()];
                    runInference();
                    applyFilter();
                    txtDichngu.setText(builder, TextView.BufferType.SPANNABLE);
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
        return view;
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
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();
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
            result[i++] = (f != null ? f : Float.NaN);
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

    void applyFilter() {
        int numLabels = getNumLabels();

        for (int j = 0; j < numLabels; ++j) {
            filterLabelProbArray[0][j] +=
                    FILTER_FACTOR * (getProbability(j) - filterLabelProbArray[0][j]);
        }

        for (int i = 1; i < FILTER_STAGES; ++i) {
            for (int j = 0; j < numLabels; ++j) {
                filterLabelProbArray[i][j] +=
                        FILTER_FACTOR * (filterLabelProbArray[i - 1][j] - filterLabelProbArray[i][j]);
            }
        }

        for (int j = 0; j < numLabels; ++j) {
            setProbability(j, filterLabelProbArray[FILTER_STAGES - 1][j]);
        }
    }

    public void setNumThreads(int numThreads) throws IOException {
        if (tflite != null) {
            tfliteOptions.setNumThreads(numThreads);
            close();
        }
        initializeModel();
    }

    /** Closes tflite to release resources. */
    public void close() {
        tflite.close();
        tflite = null;
    }

    private void initializeModel() throws IOException {
        if (tflite == null) {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(getActivity(), getModelPath());
            tflite = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(getActivity().getAssets().open(getLabelPath())));
        String line;
        line = reader.readLine();
        reader.close();

        StringTokenizer tokenizer = new StringTokenizer(line, ",");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            labelList.add(token);
        }
        return labelList;
    }

    private void printTopKLabels(SpannableStringBuilder builder) {
        for (int i = 0; i < getNumLabels(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i), getNormalizedProbability(i)));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }

        final int size = sortedLabels.size();
        for (int i = 0; i < size; i++) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            SpannableString span =
                    new SpannableString(String.format("%s:  %4.2f\n", label.getKey(), label.getValue()));
            int color;
            // Make it white when probability larger than threshold.
            if (label.getValue() > GOOD_PROB_THRESHOLD) {
                color = Color.BLACK;
            } else {
                color = SMALL_COLOR;
            }
            // Make first item bigger.
            if (i == size - 1) {
                float sizeScale = (i == size - 1) ? 1.75f : 0.8f;
                span.setSpan(new RelativeSizeSpan(sizeScale), 0, span.length(), 0);
            }
            span.setSpan(new ForegroundColorSpan(color), 0, span.length(), 0);
            builder.insert(0, span);
        }
    }

    protected int getNumLabels() {
        return labelList.size();
    }
    protected String getModelPath() {
        return "model.tflite";
    }
    protected String getLabelPath() {
        return "labels.txt";
    }
    protected float getProbability(int labelIndex) {
        return labelProbArray[0][labelIndex];
    }
    protected void setProbability(int labelIndex, Number value) {
        labelProbArray[0][labelIndex] = value.floatValue();
    }
    protected float getNormalizedProbability(int labelIndex) {
        // TODO the following value isn't in [0,1] yet, but may be greater. Why?
        return getProbability(labelIndex);
    }
    protected void runInference() {
        tflite.run(sequence, labelProbArray);
    }





}
