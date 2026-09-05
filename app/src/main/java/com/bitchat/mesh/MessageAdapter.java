package com.bitchat.mesh;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends ArrayAdapter<Message> {

    private Context context;
    private List<Message> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(Context context, List<Message> messages) {
        super(context, 0, messages);
        this.context = context;
        this.messages = messages;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
        }

        Message message = messages.get(position);

        LinearLayout messageContainer = convertView.findViewById(R.id.messageContainer);
        LinearLayout messageBubble = convertView.findViewById(R.id.messageBubble);
        TextView tvSender = convertView.findViewById(R.id.tvSender);
        TextView tvMessageBody = convertView.findViewById(R.id.tvMessageBody);
        TextView tvTime = convertView.findViewById(R.id.tvTime);

        tvMessageBody.setText(message.getContent());
        tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));

        if (message.isMine()) {
            messageContainer.setGravity(Gravity.END);
            messageBubble.setBackgroundColor(Color.parseColor("#DCF8C6")); // WhatsApp light green
            tvSender.setVisibility(View.GONE);
        } else {
            messageContainer.setGravity(Gravity.START);
            messageBubble.setBackgroundColor(Color.parseColor("#FFFFFF")); // White
            tvSender.setVisibility(View.VISIBLE);
            tvSender.setText(message.getSenderName());
        }

        return convertView;
    }
}
