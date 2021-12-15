package com.example.mainfinal;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.provider.MediaStore;
import android.telecom.VideoProfile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import edmt.dev.videoplayer.VideoPlayerRecyclerView;
import edmt.dev.videoplayer.adapter.VideoPlayerRecyclerAdapter;
import edmt.dev.videoplayer.model.MediaObject;
import edmt.dev.videoplayer.utils.VerticalSpacingItemDecorator;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Home#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Home extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public Home() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Home.
     */
    // TODO: Rename and change types and number of parameters
    public static Home newInstance(String param1, String param2) {
        Home fragment = new Home();
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


    @BindView(R.id.instruction)
    VideoPlayerRecyclerView instruction;
    View view1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        view1 = new Home().getView();

        ButterKnife.bind(this,view);
        init();

        return view;
    }

    private void init() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        instruction.setLayoutManager(layoutManager);

        VerticalSpacingItemDecorator verticalSpacingItemDecorator = new VerticalSpacingItemDecorator(19);
        instruction.addItemDecoration(verticalSpacingItemDecorator);

        ArrayList<MediaObject> sourceVideos = new ArrayList(sampleVideoList());
        instruction.setMediaObjects(sourceVideos);
        VideoPlayerRecyclerAdapter adapter = new VideoPlayerRecyclerAdapter(sourceVideos,initGlide());
        instruction.setAdapter(adapter);
    }

    private RequestManager initGlide() {
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.white_background)
                .error(R.drawable.white_background);

        return Glide.with(this).setDefaultRequestOptions(options);
    }

    private List<MediaObject> sampleVideoList() {
        return Arrays.asList(
                new MediaObject("Our Application","http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4","https://i.ytimg.com/vi/aqz-KE-bpKQ/maxresdefault.jpg",""),
                new MediaObject("Sign To Text Instruction","http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4","https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/ElephantsDreamPoster.jpg/1200px-ElephantsDreamPoster.jpg",""),
                new MediaObject("Voice To Text Instruction","http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4","https://i.ytimg.com/vi/Dr9C2oswZfA/maxresdefault.jpg",""),
                new MediaObject("Dictionary Instruction","http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4","https://assets.tvokids.com/prod/s3fs-public/small-tile-images/tileSM_bigEscape1.jpg","")
        );
    }
}