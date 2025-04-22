package com.seafile.seadroid2.view;

import static com.seafile.seadroid2.config.Constants.DP.DP_4;
import static com.seafile.seadroid2.config.Constants.DP.DP_8;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.CollectionUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.bumptech.glide.Glide;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.internal.LinkedTreeMap;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.config.ColumnType;
import com.seafile.seadroid2.config.GlideLoadConfig;
import com.seafile.seadroid2.framework.model.sdoc.FileProfileConfigModel;
import com.seafile.seadroid2.framework.model.sdoc.FileRecordWrapperModel;
import com.seafile.seadroid2.framework.model.sdoc.MetadataConfigDataModel;
import com.seafile.seadroid2.framework.model.sdoc.MetadataModel;
import com.seafile.seadroid2.framework.model.sdoc.OptionsTagModel;
import com.seafile.seadroid2.framework.model.sdoc.RecordResultModel;
import com.seafile.seadroid2.framework.model.sdoc.SDocTagModel;
import com.seafile.seadroid2.framework.model.user.UserModel;
import com.seafile.seadroid2.framework.util.SLogs;
import com.seafile.seadroid2.framework.util.Utils;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SDocDetailView extends LinearLayout {
    public SDocDetailView(Context context) {
        super(context);
        init();
    }

    public SDocDetailView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SDocDetailView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SDocDetailView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setOrientation(LinearLayout.VERTICAL);
    }

    private FileProfileConfigModel configModel;
    private boolean isNightMode;
    private HashMap<String, SDocTagModel> tagMap;

    private void setNightMode(boolean isNightMode) {
        this.isNightMode = isNightMode;
    }

    public void setData(FileProfileConfigModel configModel) {
        this.configModel = configModel;

        if (configModel == null) {
            throw new IllegalArgumentException("configModel is null");
        }

        if (configModel.detail == null) {
            throw new IllegalArgumentException("detail is null");
        }

        initFixedValueIfMetadataNotEnable();

        if (configModel.recordWrapperModel == null || CollectionUtils.isEmpty(configModel.recordWrapperModel.metadata)) {
            throw new IllegalArgumentException("metadatas is null");
        }


        convert();
    }


    /**
     * default field
     */
    private void initFixedValueIfMetadataNotEnable() {
        if (configModel.recordWrapperModel != null) {
            return;
        }

        FileRecordWrapperModel recordWrapperModel = new FileRecordWrapperModel();

        RecordResultModel sizeModel = new RecordResultModel();
        sizeModel._size = configModel.detail.getSize();
        sizeModel._file_modifier = configModel.detail.getLastModifierEmail();
        sizeModel._file_mtime = configModel.detail.getLastModified();
        recordWrapperModel.results = new ArrayList<>();
        recordWrapperModel.results.add(sizeModel);

        recordWrapperModel.metadata = new ArrayList<>();

        //size
        MetadataModel sizeMetadataModel = new MetadataModel();
        sizeMetadataModel.key = "_size";
        sizeMetadataModel.name = "_size";
        sizeMetadataModel.type = ColumnType.NUMBER;
        recordWrapperModel.metadata.add(sizeMetadataModel);

        //modifier
        MetadataModel modifierMetadataModel = new MetadataModel();
        modifierMetadataModel.key = "_file_modifier";
        modifierMetadataModel.name = "_file_modifier";
        modifierMetadataModel.type = ColumnType.TEXT;
        recordWrapperModel.metadata.add(modifierMetadataModel);

        //mtime
        MetadataModel mTimeMetadataModel = new MetadataModel();
        mTimeMetadataModel.key = "_file_mtime";
        mTimeMetadataModel.name = "_file_mtime";
        mTimeMetadataModel.type = ColumnType.DATE;
        recordWrapperModel.metadata.add(mTimeMetadataModel);

        configModel.recordWrapperModel = recordWrapperModel;

    }


    private void convert() {
        if (configModel.tagWrapperModel != null) {
            if (!CollectionUtils.isEmpty(configModel.tagWrapperModel.results)) {
                List<SDocTagModel> tags = configModel.tagWrapperModel.results.stream().map(recordResultModel -> {
                    SDocTagModel tagModel = new SDocTagModel();
                    tagModel.id = recordResultModel._id;
                    tagModel.name = recordResultModel._tag_name;
                    tagModel.color = recordResultModel._tag_color;
                    return tagModel;
                }).collect(Collectors.toList());

                tagMap = new HashMap<>();
                for (SDocTagModel tag : tags) {
                    tagMap.put(tag.id, tag);
                }
            }
        }

        List<MetadataModel> metadatas = configModel.recordWrapperModel.metadata;
        for (MetadataModel metadata : metadatas) {
            if ("_file_modifier".equals(metadata.key)) {
                metadata.type = "collaborator";
                metadata.value = CollectionUtils.newArrayList(getValueByKey(metadata.name));
            } else {
                Object v = getValueByKey(metadata.name);
                metadata.value = v;
            }
        }

        for (MetadataModel metadata : metadatas) {
            if (metadata.key.startsWith("_")) {
                if (_supportedField.contains(metadata.key)) {
                    addMetadataView(metadata);
                }
            } else {
                addMetadataView(metadata);
            }
        }
    }

    private void addMetadataView(MetadataModel metadata) {
        parseViewByType(metadata);
    }

    private final List<String> _supportedField = List.of("_size", "_file_modifier", "_file_mtime", "_description", "_collaborators", "_reviewer", "_status", "_tags", "_location");

    private Object getValueByKey(String key) {
        if (configModel.recordWrapperModel.results == null || configModel.recordWrapperModel.results.isEmpty()) {
            return null;
        }

        RecordResultModel model = configModel.recordWrapperModel.results.get(0);
        try {
            Field field = RecordResultModel.class.getDeclaredField(key);
            field.setAccessible(true);
            return field.get(model);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            SLogs.e(e);
            return null;
        }
    }

    private int getResNameByKey(String key) {
        switch (key) {
            case "_description":
                return R.string.description;
            case "_file_modifier":
                return R.string._last_modifier;
            case "_file_mtime":
                return R.string._last_modified_time;
            case "_status":
                return R.string._file_status;
            case "_collaborators":
                return R.string._file_collaborators;
            case "_size":
                return R.string._size;
            case "_reviewer":
                return R.string._reviewer;
            case "_in_progress":
                return R.string._in_progress;
            case "_in_review":
                return R.string._in_review;
            case "_done":
                return R.string._done;
            case "_outdated":
                return R.string._outdated;
            case "_location":
                return R.string._location;
            case "_tags":
                return R.string._tags;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Resources.ID_NULL;
        }
        return 0;
    }

    public void parseViewByType(MetadataModel metadata) {
        final String type = metadata.type;
        LinearLayout view = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_details_keyview_valuecontainer, null);

        int resStrId = getResNameByKey(metadata.key);
        if (resStrId != 0) {
            view.<TextView>findViewById(R.id.text_title).setText(resStrId);
        } else {
            view.<TextView>findViewById(R.id.text_title).setText(metadata.name);
        }

        view.<ImageView>findViewById(R.id.text_icon).setImageResource(getIconByColumnType(metadata.type, metadata.key));

        if (metadata.value == null) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            ltr.<TextView>findViewById(R.id.text_view).setText(R.string.empty);
            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());

            addViewToThis(view);
            return;
        }

        if (TextUtils.equals(ColumnType.TEXT, type)) {
            parseText(view, metadata);
        } else if (TextUtils.equals(ColumnType.LONG_TEXT, type)) {
            parseLongText(view, metadata);
        } else if (TextUtils.equals(ColumnType.NUMBER, type)) {
            parseNumber(view, metadata);
        } else if (TextUtils.equals(ColumnType.DATE, type)) {
            parseDate(view, metadata);
        } else if (TextUtils.equals(ColumnType.DURATION, type)) {
            parseDuration(view, metadata);
        } else if (TextUtils.equals(ColumnType.COLLABORATOR, type)) {
            parseCollaborator(view, metadata);
        } else if (TextUtils.equals(ColumnType.SINGLE_SELECT, type)) {
            parseSingleSelect(view, metadata);
        } else if (TextUtils.equals(ColumnType.MULTIPLE_SELECT, type)) {
            parseMultiSelect(view, metadata);
        } else if (TextUtils.equals(ColumnType.EMAIL, type)) {
            parseText(view, metadata);
        } else if (TextUtils.equals(ColumnType.URL, type)) {
            parseText(view, metadata);
        } else if (TextUtils.equals(ColumnType.RATE, type)) {
            parseRate(view, metadata);
        } else if (TextUtils.equals(ColumnType.GEOLOCATION, type)) {
            parseGeoLocation(view, metadata);
        } else if (TextUtils.equals(ColumnType.LINK, type)) {

            //tag
            if (TextUtils.equals("_tags", metadata.key)) {
                parseTag(view, metadata);
            } else {
                parseLink(view, metadata);
            }
        } else if (TextUtils.equals(ColumnType.IMAGE, type)) {
            parseImage(view, metadata);
        } else if (TextUtils.equals(ColumnType.FILE, type)) {
            parseFile(view, metadata);
        } else if (TextUtils.equals(ColumnType.CHECKBOX, type)) {
            parseCheckbox(view, metadata);
        } else if (TextUtils.equals(ColumnType.DIGITAL_SIGN, type)) {
            parseDigitalSign(view, metadata);
        }

        addViewToThis(view);
    }

    private FlexboxLayout.LayoutParams getFlexParams() {
        FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flexLayoutParams.bottomMargin = DP_4;
        flexLayoutParams.rightMargin = DP_4;
        return flexLayoutParams;
    }

    private void addViewToThis(View view) {
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = DP_8;
        this.addView(view, ll);
    }

    private int getIconByColumnType(String type, String key) {
        return switch (type) {
            case ColumnType.TEXT -> R.drawable.ic_single_line_text;
            case ColumnType.COLLABORATOR -> R.drawable.ic_user_collaborator;
            case ColumnType.IMAGE -> R.drawable.ic_picture;
            case ColumnType.FILE -> R.drawable.ic_file_alt_solid;
            case ColumnType.DATE -> R.drawable.ic_calendar_alt_solid;
            case ColumnType.SINGLE_SELECT -> R.drawable.ic_single_election;
            case ColumnType.DURATION -> R.drawable.ic_duration;
            case ColumnType.MULTIPLE_SELECT -> R.drawable.ic_multiple_selection;
            case ColumnType.CHECKBOX -> R.drawable.ic_check_square_solid;
            case ColumnType.GEOLOCATION -> R.drawable.ic_location;
            case ColumnType.EMAIL -> R.drawable.ic_email;
            case ColumnType.LONG_TEXT -> R.drawable.ic_long_text;
            case ColumnType.NUMBER -> R.drawable.ic_number;
            case ColumnType.RATE -> R.drawable.baseline_starred_new_24;
            case ColumnType.URL -> R.drawable.ic_url;
            case ColumnType.LINK ->
                    "_tags".equals(key) ? R.drawable.baseline_tag_24 : R.drawable.ic_links;

            default -> R.drawable.ic_single_line_text;
        };

    }

    private void parseText(LinearLayout view, MetadataModel model) {
        if (model.value instanceof String) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            String v = model.value.toString();
            if (!TextUtils.isEmpty(v)) {
                v = v.replace("\n", "").trim();
                ltr.<TextView>findViewById(R.id.text_view).setText(v);
            } else {
                ltr.<TextView>findViewById(R.id.text_view).setText(R.string.empty);
            }

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseLongText(LinearLayout view, MetadataModel model) {
        if (model.value instanceof String) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            String v = model.value.toString();
            v = v.replace("\n", "").trim();
            ltr.<TextView>findViewById(R.id.text_view).setText(v);

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseDuration(LinearLayout view, MetadataModel model) {
        if (model.value instanceof String) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            ltr.<TextView>findViewById(R.id.text_view).setText(model.value.toString());

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseNumber(LinearLayout view, MetadataModel model) {
        if (model.value instanceof Number number) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);

            ltr.<TextView>findViewById(R.id.text_view).setText(Utils.readableFileSize(number.intValue()));

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseDate(LinearLayout view, MetadataModel model) {
        if (model.value instanceof OffsetDateTime date) {
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);

            String temp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", " ");
            ltr.<TextView>findViewById(R.id.text_view).setText(temp);

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseGeoLocation(LinearLayout view, MetadataModel model) {
        if (model.value instanceof LinkedTreeMap) {
            LinkedTreeMap<String, Object> treeMap = (LinkedTreeMap<String, Object>) model.value;

            List<MetadataConfigDataModel> configDataModelList = model.configData;
            if (CollectionUtils.isEmpty(configDataModelList)) {
                return;
            }
            MetadataConfigDataModel columnDataModel = configDataModelList.get(0);
            String geo_format = columnDataModel.geo_format;

            String content = "";
            if (TextUtils.equals("lng_lat", geo_format)) {
                String lat = treeMap.get("lat").toString();
                String lng = treeMap.get("lng").toString();
                String formatLat = Utils.convertLatitude(lat);
                String formatLng = Utils.convertLongitude(lng);
                content = formatLat + ", " + formatLng;
            } else if (TextUtils.equals("geolocation", geo_format)) {
                String province = treeMap.get("province").toString();
                String city = treeMap.get("city").toString();
                String dis = treeMap.get("district").toString();
                String detail = treeMap.get("detail").toString();
                content = province + city + dis + detail;
            } else if (TextUtils.equals("country_region", geo_format)) {
                content = treeMap.get("country_region").toString();
            } else if (TextUtils.equals("province", geo_format)) {
                content = treeMap.get("province").toString();
            } else if (TextUtils.equals("province_city", geo_format)) {
                String province = treeMap.get("province").toString();
                String city = treeMap.get("city").toString();
                content = province + city;
            } else if (TextUtils.equals("province_city_district", geo_format)) {
                String province = treeMap.get("province").toString();
                String city = treeMap.get("city").toString();
                String dis = treeMap.get("district").toString();
                content = province + city + dis;
            }

            if (TextUtils.equals(",", content.trim())) {
                content = getResources().getString(R.string.empty);
            }

            if (TextUtils.isEmpty(content.trim())) {
                content = getResources().getString(R.string.empty);
            }
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            ltr.<TextView>findViewById(R.id.text_view).setText(content);

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    //container
    private void parseCollaborator(LinearLayout view, MetadataModel model) {
        if (model.value instanceof ArrayList) {
            ArrayList<String> arrayList = (ArrayList<String>) model.value;
            for (String userEmail : arrayList) {
                UserModel userModel = getRelatedUserByEmail(userEmail);
                if (userModel == null) {
                    continue;
                }

                View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_avatar_username_round, null);
                TextView user_name_text_view = ltr.findViewById(R.id.user_name);
                ShapeableImageView imageView = ltr.findViewById(R.id.user_avatar);
                Glide.with(imageView)
                        .load(userModel.getAvatarUrl())
                        .apply(GlideLoadConfig.getOptions())
                        .into(imageView);

                user_name_text_view.setText(userModel.getName());
                view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
            }
        }
    }

    private UserModel getRelatedUserByEmail(String email) {
        if (configModel.users == null || configModel.users.user_list == null) {
            return null;
        }

        Optional<UserModel> op = configModel.users.user_list.stream().filter(f -> f.getEmail().equals(email)).findFirst();
        return op.orElse(null);
    }

    private void parseSingleSelect(LinearLayout view, MetadataModel model) {
        if (model.configData == null || CollectionUtils.isEmpty(model.configData)) {
            return;
        }

        MetadataConfigDataModel configDataModel = model.configData.get(0);
        if (configDataModel.options == null || CollectionUtils.isEmpty(configDataModel.options)) {
            return;
        }

        if (model.value instanceof String && !TextUtils.isEmpty(model.value.toString())) {
            String value = (String) model.value;

            OptionsTagModel option = configDataModel.options.stream().filter(f -> f.id.equals(value)).findFirst().get();
            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_detail_text_round, null);
            TextView textView = ltr.findViewById(R.id.text);
            MaterialCardView cardView = ltr.findViewById(R.id.card_view);

            int resStrId = getResNameByKey(option.id);
            if (resStrId != 0) {
                textView.setText(resStrId);
            } else {
                textView.setText(option.name);
            }

            if (!TextUtils.isEmpty(option.textColor)) {
                textView.setTextColor(Color.parseColor(option.textColor));
            }

            if (!TextUtils.isEmpty(option.color)) {
                cardView.setCardBackgroundColor(Color.parseColor(option.color));
            }

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
        }
    }

    private void parseMultiSelect(LinearLayout view, MetadataModel model) {
        if (model.configData == null || CollectionUtils.isEmpty(model.configData)) {
            return;
        }

        MetadataConfigDataModel configDataModel = model.configData.get(0);
        if (configDataModel.options == null || CollectionUtils.isEmpty(configDataModel.options)) {
            return;
        }

        if (model.value instanceof ArrayList) {
            ArrayList<String> arrayList = (ArrayList<String>) model.value;
            for (String key : arrayList) {
                OptionsTagModel option = configDataModel.options.stream().filter(f -> f.id.equals(key)).findFirst().get();
                View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_detail_text_round, null);
                TextView textView = ltr.findViewById(R.id.text);
                MaterialCardView cardView = ltr.findViewById(R.id.card_view);

                int resStrId = getResNameByKey(option.id);
                if (resStrId != 0) {
                    textView.setText(resStrId);
                } else {
                    textView.setText(option.name);
                }


//            if (!TextUtils.isEmpty(option.borderColor)) {
//            }

                if (!TextUtils.isEmpty(option.textColor)) {
                    textView.setTextColor(Color.parseColor(option.textColor));
                }

                if (!TextUtils.isEmpty(option.color)) {
                    cardView.setCardBackgroundColor(Color.parseColor(option.color));
                }

                view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
            }
        }
    }

    private void parseTag(LinearLayout view, MetadataModel model) {
        if (model.value instanceof ArrayList) {
            if (tagMap == null) {
                return;
            }

            FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
            FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flexLayoutParams.bottomMargin = DP_4;
            flexLayoutParams.rightMargin = DP_4;

            ArrayList<LinkedTreeMap<String, String>> arrayList = (ArrayList<LinkedTreeMap<String, String>>) model.value;
            if (!CollectionUtils.isEmpty(arrayList)) {
                for (LinkedTreeMap<String, String> map : arrayList) {
                    String rowId = map.get("row_id");
                    SDocTagModel tagModel = tagMap.get(rowId);
                    if (tagModel == null) {
                        continue;
                    }

                    View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_detail_tag, null);

                    MaterialCardView cardView = ltr.findViewById(R.id.indicator);

                    if (!TextUtils.isEmpty(tagModel.color)) {
                        cardView.setCardBackgroundColor(Color.parseColor(tagModel.color));
                    }

                    TextView textView = ltr.findViewById(R.id.text);
                    textView.setMaxLines(1);
                    textView.setEllipsize(TextUtils.TruncateAt.END);
                    textView.setText(tagModel.name);
                    flexboxLayout.addView(ltr, flexLayoutParams);
                }

            }
        }
    }


    private void addNotSupportedLayoutView(LinearLayout view) {

        View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
        ltr.<TextView>findViewById(R.id.text_view).setText(R.string.not_supported);

        view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
    }

    private void parseImage(LinearLayout view, MetadataModel model) {
        addNotSupportedLayoutView(view);

//        if (model.value instanceof ArrayList) {
//            FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
//
//            int wh = SizeUtils.dp2px(24);
//            FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(wh, wh);
//            flexLayoutParams.bottomMargin = DP_4;
//            flexLayoutParams.rightMargin = DP_4;
//
//            ArrayList<String> imgList = (ArrayList<String>) model.value;
//            for (int i = 0; i < imgList.size(); i++) {
//                String url = imgList.get(i);
//                ImageView imageView = new ImageView(flexboxLayout.getContext());
//                Glide.with(imageView)
//                        .load(GlideLoadConfig.getGlideUrl(url))
//                        .apply(GlideLoadConfig.getOptions())
//                        .into(imageView);
//                flexboxLayout.addView(imageView, flexLayoutParams);
//            }
//        }
    }

    private void parseFile(LinearLayout view, MetadataModel model) {
        addNotSupportedLayoutView(view);
//        if (model.value instanceof ArrayList) {
//            FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
//
//            int wh = SizeUtils.dp2px(24);
//            FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(wh, wh);
//            flexLayoutParams.bottomMargin = DP_4;
//            flexLayoutParams.rightMargin = DP_4;
//
//            ArrayList<LinkedTreeMap<String, String>> fileList = (ArrayList<LinkedTreeMap<String, String>>) model.value;
//            for (LinkedTreeMap<String, String> treeMap : fileList) {
//                ImageView imageView = new ImageView(flexboxLayout.getContext());
//                String url = treeMap.get("url").toString();
//
//                int rid = MimeTypes.getFileIconFromExtension(url);
//                imageView.setImageResource(rid);
//                flexboxLayout.addView(imageView, flexLayoutParams);
//            }
//        }
    }

    private void parseRate(LinearLayout view, MetadataModel model) {

        if (model.value == null) {

            View ltr = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_textview, null);
            ltr.<TextView>findViewById(R.id.text_view).setText(R.string.empty);

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, getFlexParams());
            return;
        }

        if (model.configData == null || CollectionUtils.isEmpty(model.configData)) {
            return;
        }

        MetadataConfigDataModel configDataModel = model.configData.get(0);

        int wh = SizeUtils.dp2px(16);
        FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(wh, wh);
        flexLayoutParams.rightMargin = DP_4;

        int selectCount = ((Number) model.value).intValue();

        for (int i = 0; i < configDataModel.rate_max_number; i++) {
            ImageView ltr = new ImageView(view.getContext());

            int t;
            if (i < selectCount) {
                if (TextUtils.isEmpty(configDataModel.rate_style_color)) {
                    t = ContextCompat.getColor(getContext(), R.color.grey);
                } else {
                    t = Color.parseColor(configDataModel.rate_style_color);
                }
            } else {
                t = ContextCompat.getColor(getContext(), R.color.light_gray);
            }

            ColorStateList stateList = ColorStateList.valueOf(t);

            ltr.setImageTintList(stateList);
            ltr.setImageResource(R.drawable.baseline_starred_new_24);

            view.<FlexboxLayout>findViewById(R.id.flex_box).addView(ltr, flexLayoutParams);
        }
    }


    //
    private void parseCheckbox(LinearLayout view, MetadataModel model) {
        addNotSupportedLayoutView(view);
//        FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
//        FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        flexLayoutParams.bottomMargin = DP_4;
//        flexLayoutParams.rightMargin = DP_4;
//        flexLayoutParams.height = SizeUtils.dp2px(30);
//
//        AppCompatCheckBox checkBox = new AppCompatCheckBox(view.getContext());
//        checkBox.setText("");
//        checkBox.setClickable(false);
//        if (model.value instanceof Boolean) {
//            checkBox.setChecked((Boolean) model.value);
//        }
//        flexboxLayout.addView(checkBox, flexLayoutParams);
    }

    private void parseLink(LinearLayout view, MetadataModel model) {
        addNotSupportedLayoutView(view);
//        if (model.value instanceof ArrayList) {
//
//            FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
//            FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//            flexLayoutParams.bottomMargin = DP_4;
//            flexLayoutParams.rightMargin = DP_4;
//
//            ArrayList<LinkedTreeMap<String, String>> arrayList = (ArrayList<LinkedTreeMap<String, String>>) model.value;
//            if (!CollectionUtils.isEmpty(arrayList)) {
//                LinkedTreeMap<String, String> hashMap = arrayList.get(0);
//                View ltr = LayoutInflater.from(flexboxLayout.getContext()).inflate(R.layout.layout_text_round, null);
//                TextView textView = ltr.findViewById(R.id.text);
//                textView.setMaxLines(1);
//                textView.setEllipsize(TextUtils.TruncateAt.END);
//                textView.setText(hashMap.get("display_value"));
//                flexboxLayout.addView(ltr, flexLayoutParams);
//
//                //
//                flexboxLayout.addView(TaskViews.getInstance().getSingleLineTextView(context, "..."));
//            }
//        }
    }

    private void parseDigitalSign(LinearLayout view, MetadataModel model) {
        addNotSupportedLayoutView(view);
//        if (model.value instanceof LinkedTreeMap) {
//            FlexboxLayout flexboxLayout = view.findViewById(R.id.flex_box);
//            FlexboxLayout.LayoutParams flexLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//            flexLayoutParams.bottomMargin = DP_4;
//            flexLayoutParams.rightMargin = DP_4;
//
//            LinkedTreeMap<String, Object> treeMap = (LinkedTreeMap<String, Object>) model.value;
//
//            ///digital-signs/2023-05/chaohui.wang%40seafile.com-1684141281136.png
//            String url = treeMap.get("sign_image_url").toString();
//
//            //https://dev.seatable.cn/thumbnail/workspace/246/asset/cf317040-a299-4311-a565-27d8b3dde319/digital-signs/2023-05/chaohui.wang%40seafile.com-1684141281136.png?size=256
//            url = IO.getSingleton().getHostUrl() + "thumbnail/workspace/" + workFlowModel.id + "/asset/" + workFlowModel.dtable_uuid + url;
//
//            ImageView imageView = Views.TableActivityViews.getImageViewWithWhAndUrl(flexboxLayout.getContext(), 24, url);
//            flexboxLayout.addView(imageView, flexLayoutParams);
//        }
    }

}
