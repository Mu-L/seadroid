package com.seafile.seadroid2.framework.data.model.repo;

import com.seafile.seadroid2.framework.data.db.entities.DirentModel;

import java.util.ArrayList;
import java.util.List;

public class DirentWrapperModel {
    public String user_perm;
    public String dir_id;
    public List<DirentModel> dirent_list = new ArrayList<>();
}
