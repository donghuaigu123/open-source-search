package com.opensourcesearch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.opensourcesearch.databinding.ItemRepoBinding;

import java.util.ArrayList;
import java.util.List;

/** 仓库搜索结果列表适配器。 */
public class RepoAdapter extends RecyclerView.Adapter<RepoAdapter.ViewHolder> {

    public interface Listener {
        void onRepoClick(Repo repo);

        void onOpenInBrowser(Repo repo);
    }

    private final List<Repo> data = new ArrayList<>();
    private final Listener listener;

    public RepoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<Repo> repos) {
        data.clear();
        if (repos != null) {
            data.addAll(repos);
        }
        notifyDataSetChanged();
    }

    public List<Repo> getData() {
        return new ArrayList<>(data);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRepoBinding binding = ItemRepoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Repo repo = data.get(position);
        ItemRepoBinding b = holder.binding;

        b.repoName.setText(repo.fullName);
        b.repoUpdated.setText(Repo.relativeTime(repo.updatedAt));

        if (repo.description == null || repo.description.trim().isEmpty()
                || "null".equals(repo.description)) {
            b.repoDesc.setText("该仓库暂无描述");
        } else {
            b.repoDesc.setText(repo.description);
        }

        Glide.with(b.repoAvatar)
                .load(repo.ownerAvatar)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(b.repoAvatar);

        if (repo.language == null || repo.language.trim().isEmpty()
                || "null".equals(repo.language)) {
            b.langDot.setVisibility(View.GONE);
            b.repoLanguage.setVisibility(View.GONE);
        } else {
            b.langDot.setVisibility(View.VISIBLE);
            b.repoLanguage.setVisibility(View.VISIBLE);
            b.langDot.setColorFilter(Repo.languageColor(repo.language));
            b.repoLanguage.setText(repo.language);
        }

        b.repoStars.setText("★ " + Repo.formatCount(repo.stars));
        b.repoForks.setText("⑂ " + Repo.formatCount(repo.forks));

        b.repoCard.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRepoClick(repo);
            }
        });
        b.btnOpenBrowser.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpenInBrowser(repo);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemRepoBinding binding;

        ViewHolder(ItemRepoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
