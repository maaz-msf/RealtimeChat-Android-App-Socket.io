package com.msf.youtubechatapp.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.msf.youtubechatapp.R;
import com.msf.youtubechatapp.models.User;

import java.util.List;
import java.util.Map;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    List<User> usersList;
    Map<String, Integer> unreadMessagesCount;
    OnUserClickListener listener;

    public UserAdapter(List<User> usersList, Map<String, Integer> unreadMessagesCount, OnUserClickListener listener) {
        this.usersList = usersList;
        this.unreadMessagesCount = unreadMessagesCount;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = usersList.get(position);
        holder.nameTextView.setText(user.getUsername()); // Display username
        holder.statusTextView.setText(user.getStatus().equals("online") ? "Online" : "Offline");
        int unreadCount = unreadMessagesCount.getOrDefault(user.getUserId(), 0);
        if (unreadCount > 0) {
            holder.unreadCountTextView.setVisibility(View.VISIBLE);
            holder.unreadCountTextView.setText(" " + unreadCount + " ");
        } else {
            holder.unreadCountTextView.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> listener.onUserClick(user.getUserId()));
    }

    @Override
    public int getItemCount() {
        return usersList.size();
    }

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView statusTextView;
        TextView unreadCountTextView;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            unreadCountTextView = itemView.findViewById(R.id.unreadCountTextView);
        }
    }
}