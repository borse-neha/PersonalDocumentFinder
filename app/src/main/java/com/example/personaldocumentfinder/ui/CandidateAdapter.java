package com.example.personaldocumentfinder.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personaldocumentfinder.R;
import com.example.personaldocumentfinder.domain.CandidateImportItem;
import com.example.personaldocumentfinder.domain.ClassificationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.CandidateViewHolder> {

    public static final List<String> ALL_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "College",
            "Identity",
            "Finance",
            "Office",
            "Vehicle",
            "Personal",
            "Other Documents"
    ));

    public interface CandidateActionListener {
        void onCategoryChanged(int position, String newCategory);
        void onRemoveCandidate(int position);
    }

    private final CandidateActionListener listener;
    private List<CandidateImportItem> candidates = new ArrayList<>();

    public CandidateAdapter(CandidateActionListener listener) {
        this.listener = listener;
    }

    public void setCandidates(List<CandidateImportItem> newCandidates) {
        this.candidates = newCandidates != null ? newCandidates : new ArrayList<CandidateImportItem>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_candidate_card, parent, false);
        return new CandidateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final CandidateViewHolder holder, int position) {
        final CandidateImportItem candidate = candidates.get(position);
        final ClassificationResult classification = candidate.getClassification();

        holder.tvIcon.setText(classification != null && classification.isDocument() ? "📄" : "📷");
        holder.tvName.setText(candidate.getFileName());
        holder.tvType.setText("Type: " + (classification != null ? classification.getDocumentType() : "Unknown"));

        // Confidence Badge
        if (classification == null || !classification.isDocument()) {
            holder.tvConfidence.setText("Rejected Photo");
            holder.tvConfidence.setTextColor(Color.parseColor("#B00020"));
        } else if (classification.getCategoryConfidence() >= 0.70f) {
            holder.tvConfidence.setText("High Confidence");
            holder.tvConfidence.setTextColor(Color.parseColor("#2E7D32"));
        } else if (classification.getCategoryConfidence() >= 0.45f) {
            holder.tvConfidence.setText("Medium Confidence");
            holder.tvConfidence.setTextColor(Color.parseColor("#E65100"));
        } else {
            holder.tvConfidence.setText("Low Confidence");
            holder.tvConfidence.setTextColor(Color.parseColor("#F57F17"));
        }

        // Analysis Reason
        if (classification != null && classification.getDetectionReason() != null && !classification.getDetectionReason().trim().isEmpty()) {
            holder.tvReason.setVisibility(View.VISIBLE);
            holder.tvReason.setText("Analysis: " + classification.getDetectionReason());
        } else {
            holder.tvReason.setVisibility(View.GONE);
        }

        // OCR Snippet
        if (classification != null && classification.getOcrText() != null && !classification.getOcrText().trim().isEmpty()) {
            holder.tvOcr.setVisibility(View.VISIBLE);
            String snippet = classification.getOcrText();
            if (snippet.length() > 120) snippet = snippet.substring(0, 120);
            holder.tvOcr.setText("OCR Snippet: \"" + snippet + "...\"");
        } else {
            holder.tvOcr.setVisibility(View.GONE);
        }

        // Duplicate
        holder.tvDuplicate.setVisibility(candidate.isDuplicate() ? View.VISIBLE : View.GONE);

        // Spinner Setup
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                holder.itemView.getContext(),
                android.R.layout.simple_spinner_item,
                ALL_CATEGORIES
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerCategory.setAdapter(spinnerAdapter);

        int selectedIdx = ALL_CATEGORIES.indexOf(candidate.getSelectedCategory());
        if (selectedIdx >= 0) {
            holder.spinnerCategory.setSelection(selectedIdx, false);
        }

        holder.spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initial = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (initial) {
                    initial = false;
                    return;
                }
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION && listener != null && pos >= 0 && pos < ALL_CATEGORIES.size()) {
                    String newCat = ALL_CATEGORIES.get(pos);
                    if (!newCat.equals(candidate.getSelectedCategory())) {
                        listener.onCategoryChanged(adapterPos, newCat);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        holder.btnExclude.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onRemoveCandidate(adapterPos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return candidates.size();
    }

    static class CandidateViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvType;
        final TextView tvConfidence;
        final TextView tvReason;
        final TextView tvOcr;
        final TextView tvDuplicate;
        final Spinner spinnerCategory;
        final Button btnExclude;

        CandidateViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tv_candidate_icon);
            tvName = itemView.findViewById(R.id.tv_candidate_name);
            tvType = itemView.findViewById(R.id.tv_candidate_type);
            tvConfidence = itemView.findViewById(R.id.tv_candidate_confidence);
            tvReason = itemView.findViewById(R.id.tv_candidate_reason);
            tvOcr = itemView.findViewById(R.id.tv_candidate_ocr);
            tvDuplicate = itemView.findViewById(R.id.tv_candidate_duplicate);
            spinnerCategory = itemView.findViewById(R.id.spinner_candidate_category);
            btnExclude = itemView.findViewById(R.id.btn_candidate_exclude);
        }
    }
}
