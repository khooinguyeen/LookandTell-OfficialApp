package com.example.mainfinal;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Dichngu#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Dichngu extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public Dichngu() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment dichngu_stop.
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_dichngu_stop, container, false);
        ImageView stopRecordBtn = view.findViewById(R.id.stopRecordBtn);
        TextView txtDichngu = view.findViewById(R.id.txtDichNgu);
        txtDichngu.setText("xin chào");

        if ( stopRecordBtn != null ) {
            stopRecordBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RequestQueue queue = Volley.newRequestQueue(requireActivity());
                    String url = "https://www.metaweather.com/api/location/search/?query=sydney"; //TODO: thay thành url của server mình

                    JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    String cityID = "";
                                    try {
                                        JSONObject cityInfo = response.getJSONObject(0);
                                        cityID = cityInfo.getString("woeid");
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    Toast.makeText(requireActivity(), "City ID = " + cityID, Toast.LENGTH_SHORT).show();
                                }
                            }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(requireActivity(), "Something wrong.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    queue.add(request);

                    // Request a string response from the provided URL.
//                    StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
//                            new Response.Listener<String>() {
//                                @Override
//                                public void onResponse(String response) {
//                                    // Display the first 500 characters of the response string.
//                                    Toast.makeText(requireActivity(), response, Toast.LENGTH_SHORT).show();
//                                }
//                            }, new Response.ErrorListener() {
//                        @Override
//                        public void onErrorResponse(VolleyError error) {
////                            txtDichngu.setText("That didn't work!"); //TODO: trả ra thông báo error
//                            Toast.makeText(requireActivity(), "Error", Toast.LENGTH_SHORT).show();
//                        }
//                    });

                    // Add the request to the RequestQueue.

                    if ( v.equals(stopRecordBtn) ) {
                        Fragment fragment = new dichngu_stop();
                        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.main,fragment).commit();
                    }
                }
            });
        }

        return view;
    }
}