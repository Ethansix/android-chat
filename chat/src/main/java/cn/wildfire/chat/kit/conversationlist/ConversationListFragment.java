package cn.wildfire.chat.kit.conversationlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.Arrays;
import java.util.List;

import cn.wildfire.chat.kit.IMServiceStatusViewModel;
import cn.wildfire.chat.kit.WfcUIKit;
import cn.wildfire.chat.kit.conversationlist.notification.ConnectionNotification;
import cn.wildfire.chat.kit.conversationlist.notification.StatusNotification;
import cn.wildfire.chat.kit.user.UserViewModel;
import cn.wildfirechat.chat.R;
import cn.wildfirechat.client.ConnectionStatus;
import cn.wildfirechat.model.Conversation;
import cn.wildfirechat.model.ConversationInfo;
import cn.wildfirechat.model.UserInfo;

public class ConversationListFragment extends Fragment {
    private RecyclerView recyclerView;
    private ConversationListAdapter adapter;
    private static final List<Conversation.ConversationType> types = Arrays.asList(Conversation.ConversationType.Single,
            Conversation.ConversationType.Group,
            Conversation.ConversationType.Channel);
    private static final List<Integer> lines = Arrays.asList(0);

    private ConversationListViewModel conversationListViewModel;
    private IMServiceStatusViewModel imServiceStatusViewModel;
    private UserViewModel userViewModel;
    private Observer<ConversationInfo> conversationInfoObserver = new Observer<ConversationInfo>() {
        @Override
        public void onChanged(@Nullable ConversationInfo conversationInfo) {
            // just handle what we care about
            if (types.contains(conversationInfo.conversation.type) && lines.contains(conversationInfo.conversation.line)) {
                adapter.submitConversationInfo(conversationInfo);
                // scroll or not?
                // recyclerView.scrollToPosition(0);
            }
        }
    };

    private Observer<Conversation> conversationRemovedObserver = new Observer<Conversation>() {
        @Override
        public void onChanged(@Nullable Conversation conversation) {
            if (conversation == null) {
                return;
            }
            if (types.contains(conversation.type) && lines.contains(conversation.line)) {
                adapter.removeConversation(conversation);
            }
        }
    };

    // 会话同步
    private Observer<Object> settingUpdateObserver = o -> reloadConversations();

    private Observer<List<UserInfo>> userInfoLiveDataObserver = (userInfos) -> {
        adapter.updateUserInfos(userInfos);
    };

    private Observer<Boolean> imStatusLiveDataObserver = (connected) -> {
        if (connected) {
            if (adapter != null && (adapter.getConversationInfos() == null || adapter.getConversationInfos().size() == 0)) {
                reloadConversations();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conversationlist_frament, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        init();
        return view;
    }

    private void init() {
        adapter = new ConversationListAdapter(this);
        conversationListViewModel = ViewModelProviders
                .of(this, new ConversationListViewModelFactory(types, lines))
                .get(ConversationListViewModel.class);
        conversationListViewModel.getConversationListAsync(types, lines)
                .observe(this, conversationInfos -> {
                    adapter.setConversationInfos(conversationInfos);
                    adapter.notifyDataSetChanged();
                });
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        DividerItemDecoration itemDecor = new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL);
        itemDecor.setDrawable(ContextCompat.getDrawable(getContext(), R.drawable.recyclerview_horizontal_divider));
        recyclerView.addItemDecoration(itemDecor);
        recyclerView.setAdapter(adapter);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        conversationListViewModel.conversationInfoLiveData().observeForever(conversationInfoObserver);
        conversationListViewModel.conversationRemovedLiveData().observeForever(conversationRemovedObserver);
        conversationListViewModel.settingUpdateLiveData().observeForever(settingUpdateObserver);

        imServiceStatusViewModel = WfcUIKit.getAppScopeViewModel(IMServiceStatusViewModel.class);
        imServiceStatusViewModel.imServiceStatusLiveData().observeForever(imStatusLiveDataObserver);

        userViewModel = WfcUIKit.getAppScopeViewModel(UserViewModel.class);
        userViewModel.userInfoLiveData().observeForever(userInfoLiveDataObserver);

        conversationListViewModel.connectionStatusLiveData().observe(this, status -> {
            switch (status) {
                case ConnectionStatus.ConnectionStatusConnecting:
                    showNotification(ConnectionNotification.class, "正在连接...");
                    break;
                case ConnectionStatus.ConnectionStatusConnected:
                    clearNotification(ConnectionNotification.class);
                    break;
                case ConnectionStatus.ConnectionStatusUnconnected:
                    showNotification(ConnectionNotification.class, "连接失败");
                    break;
                default:
                    break;
            }
        });
    }

    private void showNotification(Class<? extends StatusNotification> clazz, Object value) {
        adapter.showStatusNotification(clazz, value);
    }

    private void clearNotification(Class<? extends StatusNotification> clazz) {
        adapter.clearStatusNotification(clazz);
    }

    private void reloadConversations() {
        conversationListViewModel.getConversationListAsync(types, lines)
                .observe(this, conversationInfos -> {
                    adapter.setConversationInfos(conversationInfos);
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        conversationListViewModel.conversationInfoLiveData().removeObserver(conversationInfoObserver);
        conversationListViewModel.conversationRemovedLiveData().removeObserver(conversationRemovedObserver);
        conversationListViewModel.settingUpdateLiveData().removeObserver(settingUpdateObserver);
        imServiceStatusViewModel.imServiceStatusLiveData().removeObserver(imStatusLiveDataObserver);
        userViewModel.userInfoLiveData().removeObserver(userInfoLiveDataObserver);
    }

}
