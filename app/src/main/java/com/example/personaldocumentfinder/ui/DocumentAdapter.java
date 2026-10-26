package com.example.personaldocumentfinder.ui;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personaldocumentfinder.R;
import com.example.personaldocumentfinder.data.DocumentEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder> {

    public interface DocumentActionListener {
        void onOpen(DocumentEntity document);
        void onRename(DocumentEntity document);
        void onChangeCategory(DocumentEntity document);
        void onToggleFavorite(DocumentEntity document);
        void onDelete(DocumentEntity document);
    }

    private final DocumentActionListener listener;
    private List<DocumentEntity> documents = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public DocumentAdapter(DocumentActionListener listener) {
        this.listener = listener;
    }

    public void setDocuments(List<DocumentEntity> newDocs) {
        this.documents = newDocs != null ? newDocs : new ArrayList<DocumentEntity>();
        notifyDataSetChanged();
    }

    public static String getFileIcon(String mimeType, String fileName) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return "🖼️";
        } else if ("application/pdf".equals(mimeType)) {
            return "📄";
        } else if (mimeType != null && mimeType.startsWith("text/")) {
            return "📝";
        } else if (fileName != null && (fileName.toLowerCase(Locale.ROOT).endsWith(".doc") || fileName.toLowerCase(Locale.ROOT).endsWith(".docx"))) {
            return "📑";
        } else {
            return "📁";
        }
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document_card, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        final DocumentEntity doc = documents.get(position);
        Context context = holder.itemView.getContext();

        holder.tvIcon.setText(getFileIcon(doc.getMimeType(), doc.getOriginalName()));
        holder.tvTitle.setText(doc.getEffectiveDisplayName());

        String formattedSize = Formatter.formatShortFileSize(context, doc.getFileSize());
        String formattedDate = dateFormat.format(new Date(doc.getDateImported()));
        holder.tvSubtitle.setText(doc.getDocumentType() + " • " + formattedSize + " • " + formattedDate);

        holder.btnCategory.setText("📂 " + doc.getCategory());
        holder.btnFavorite.setText(doc.isFavorite() ? "⭐ Favorited" : "☆ Favorite");

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpen(doc);
            }
        });

        holder.btnCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onChangeCategory(doc);
            }
        });

        holder.btnRename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onRename(doc);
            }
        });

        holder.btnFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onToggleFavorite(doc);
            }
        });

        holder.btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpen(doc);
            }
        });

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(doc);
            }
        });
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    static class DocumentViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final Button btnCategory;
        final Button btnRename;
        final Button btnFavorite;
        final Button btnOpen;
        final Button btnDelete;

        DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tv_doc_icon);
            tvTitle = itemView.findViewById(R.id.tv_doc_title);
            tvSubtitle = itemView.findViewById(R.id.tv_doc_subtitle);
            btnCategory = itemView.findViewById(R.id.btn_change_category);
            btnRename = itemView.findViewById(R.id.btn_rename);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            btnOpen = itemView.findViewById(R.id.btn_open);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
