package com.example.personaldocumentfinder.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personaldocumentfinder.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public static class CategoryItem {
        private final String name;
        private final String icon;

        public CategoryItem(String name, String icon) {
            this.name = name;
            this.icon = icon;
        }

        public String getName() {
            return name;
        }

        public String getIcon() {
            return icon;
        }
    }

    public static final List<CategoryItem> CATEGORY_ITEMS = Collections.unmodifiableList(Arrays.asList(
            new CategoryItem("College", "🎓"),
            new CategoryItem("Identity", "🪪"),
            new CategoryItem("Finance", "💳"),
            new CategoryItem("Office", "💼"),
            new CategoryItem("Vehicle", "🚗"),
            new CategoryItem("Personal", "📁"),
            new CategoryItem("Other Documents", "📄")
    ));

    public interface OnCategoryClickListener {
        void onCategoryClick(String categoryName);
    }

    private final OnCategoryClickListener listener;
    private Map<String, Integer> categoryCounts = Collections.emptyMap();
    private boolean isDatabaseLoaded = false;

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategoryCounts(Map<String, Integer> counts, boolean isLoaded) {
        this.categoryCounts = counts != null ? counts : Collections.<String, Integer>emptyMap();
        this.isDatabaseLoaded = isLoaded;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        final CategoryItem item = CATEGORY_ITEMS.get(position);
        holder.tvIcon.setText(item.getIcon());
        holder.tvName.setText(item.getName());

        if (!isDatabaseLoaded) {
            holder.tvCount.setText("—");
        } else {
            Integer count = categoryCounts.get(item.getName());
            int c = count != null ? count : 0;
            holder.tvCount.setText(c + " document(s)");
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onCategoryClick(item.getName());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return CATEGORY_ITEMS.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvCount;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tv_category_icon);
            tvName = itemView.findViewById(R.id.tv_category_name);
            tvCount = itemView.findViewById(R.id.tv_category_count);
        }
    }
}
