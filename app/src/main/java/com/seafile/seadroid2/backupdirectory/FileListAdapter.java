package com.seafile.seadroid2.backupdirectory;


import android.app.Activity;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.seafile.seadroid2.R;

import java.util.List;
public class FileListAdapter extends RecyclerView.Adapter<FileListViewHolder> {
    private List<FileBean> mListData;
    private Context mContext;
    private OnFileItemClickListener onItemClickListener;



    public void setOnItemClickListener(OnFileItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public FileListAdapter(Activity context, List<FileBean> listData) {
        mListData = listData;
        mContext = context;
    }

    @Override
    public FileListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_files_list, parent, false);
        FileListViewHolder fileListViewHolder = new FileListViewHolder(view);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int layoutPosition = fileListViewHolder.getLayoutPosition();
                if (onLongClickLisenter!=null)
                {
                    onLongClickLisenter.onLongClickLisenter(layoutPosition);
                }
                return true;
            }
        });

        return fileListViewHolder;
    }

    @Override
    public void onBindViewHolder(final FileListViewHolder holder, int positon) {
        final FileBean fileBean = mListData.get(positon);



        holder.imgvFiletype.setImageResource(fileBean.getFileImgType());
        boolean isFile = fileBean.isFile();
        if (isFile){
            holder.tvFileDetail.setText(String.format("大小:%s", fileBean.getSize()));
        }else {
            holder.tvFileDetail.setText(String.format(" 文件:%s | 文件夹:%s", fileBean.getChildrenFileNumber(),fileBean.getChildrenDirNumber()));
        }

        if (fileBean.isVisible()){
            if(!isFile){
                holder.checkBoxFile.setVisibility(View.VISIBLE);
            }else {
                holder.checkBoxFile.setVisibility(View.GONE);
            }
            holder.imgvEnter.setVisibility(View.INVISIBLE);
            holder.checkBoxFile.setChecked(fileBean.isChecked());
        }else {
            holder.checkBoxFile.setVisibility(View.INVISIBLE);
            if (!isFile){
                holder.imgvEnter.setVisibility(View.VISIBLE);
            }else {
                holder.imgvEnter.setVisibility(View.INVISIBLE);
            }
            holder.checkBoxFile.setChecked(false);
        }
        holder.tvFileName.setText(fileBean.getFileName());
        holder.layoutRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClickListener != null) {
                    onItemClickListener.click(holder.getAdapterPosition());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        if(mListData==null){
            return 0;
        }else {
            return mListData.size();
        }
    }

    public void updateListData(List<FileBean> mListData) {
        this.mListData = mListData;
    }

    private OnFileItemLongClickListener onLongClickLisenter;

    public void setOnLongClickLisenter(OnFileItemLongClickListener onLongClickLisenter) {
        this.onLongClickLisenter = onLongClickLisenter;
    }
}