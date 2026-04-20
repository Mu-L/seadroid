package com.seafile.seadroid2.framework.model.sdoc;

import org.apache.commons.lang3.StringUtils;

public class TextTypeModel {
    /**
     * undo
     * redo
     * paragraph,title,subtitle,h1,h2,h3,h4,h5,h6
     * unordered-list
     * ordered-list
     * checkbox
     * local-image: insert image into sdoc
     */
    public String type;

    public TextTypeModel(String type) {
        if (StringUtils.equals("local_image", type)) {
            type = "local-image";
        }
        this.type = type;
    }
}
