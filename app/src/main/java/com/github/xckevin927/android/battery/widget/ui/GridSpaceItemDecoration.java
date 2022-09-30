package com.github.xckevin927.android.battery.widget.ui;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class GridSpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final String TAG = "GridSpaceItemDecoration";

    private final int mSpanCount;//横条目数量
    private final int mRowSpacing;//行间距
    private final int mColumnSpacing;// 列间距

    /**
     * @param spanCount     列数
     * @param rowSpacing    行间距
     * @param columnSpacing 列间距
     */
    public GridSpaceItemDecoration(int spanCount, int rowSpacing, int columnSpacing) {
        this.mSpanCount = spanCount;
        this.mRowSpacing = rowSpacing;
        this.mColumnSpacing = columnSpacing;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view); // 获取view 在adapter中的位置。
        int column = position % mSpanCount; // view 所在的列

        outRect.left = column * mColumnSpacing / mSpanCount; // column * (列间距 * (1f / 列数))
        outRect.right = mColumnSpacing - (column + 1) * mColumnSpacing / mSpanCount; // 列间距 - (column + 1) * (列间距 * (1f /列数))

        // 如果position > 行数，说明不是在第一行，则不指定行高，其他行的上间距为 top=mRowSpacing
        if (position >= mSpanCount) {
            outRect.top = mRowSpacing; // item top
        }
    }
}