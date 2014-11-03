/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tsupport.ui.Adapters;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;

import org.tsupport.SQLite.SQLiteCursor;
import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.ContactsController;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessageObject;
import org.tsupport.android.MessagesController;
import org.tsupport.android.MessagesStorage;
import org.tsupport.messenger.ByteBufferDesc;
import org.tsupport.messenger.ConnectionsManager;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.RPCRequest;
import org.tsupport.messenger.TLClassStore;
import org.tsupport.messenger.TLObject;
import org.tsupport.messenger.TLRPC;
import org.tsupport.messenger.UserConfig;
import org.tsupport.messenger.Utilities;
import org.tsupport.ui.Cells.ChatOrUserCell;
import org.tsupport.ui.Cells.DialogCell;
import org.tsupport.ui.Views.SettingsSectionLayout;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessagesActivitySearchAdapter extends BaseContactsSearchAdapter {
    private Context mContext;
    private Timer searchTimer;
    private ArrayList<TLObject> searchResult = new ArrayList<TLObject>();
    private ArrayList<Integer> searchResultsId = new ArrayList<Integer>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<CharSequence>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<MessageObject>();
    private String lastSearchTextDialogs;
    private String lastSearchTextMessages;
    private int currentSearchType;
    private long reqId = 0;
    private int lastReqId;
    private MessagesActivitySearchAdapterDelegate delegate;

    public static interface MessagesActivitySearchAdapterDelegate {
        public abstract void searchStateChanged(boolean searching);
    }

    public MessagesActivitySearchAdapter(Context context) {
        mContext = context;
    }

    public void setDelegate(MessagesActivitySearchAdapterDelegate delegate) {
        this.delegate = delegate;
    }

    private void searchMessagesInternal(final String query) {
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRpc(reqId, true);
            reqId = 0;
        }
        if (query == null || query.length() == 0) {
            searchResultMessages.clear();
            lastReqId = 0;
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(false);
            }
            searchResultsId.clear();
            updateSearchResults(new ArrayList<TLObject>(), new ArrayList<CharSequence>(), new ArrayList<TLRPC.User>());
            return;
        }
        final ArrayList<TLObject> resultArray = new ArrayList<TLObject>();
        final ArrayList<CharSequence> resultArrayNames = new ArrayList<CharSequence>();
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 256;
        req.peer = new TLRPC.TL_inputPeerEmpty();
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        if (delegate != null) {
            delegate.searchStateChanged(true);
        }
        reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                for (TLRPC.User user: res.users) {
                                    if (!searchResultsId.contains(user.id)) {
                                        resultArrayNames.add(Utilities.generateSearchName(user.first_name, user.last_name, query));
                                        resultArray.add(user);
                                        searchResultsId.add(user.id);
                                    }
                                }
                                for (TLRPC.Chat chat: res.chats) {
                                    if (!searchResultsId.contains(chat.id)) {
                                        resultArrayNames.add(Utilities.generateSearchName(chat.title, null, query));
                                        resultArray.add(chat);
                                        searchResultsId.add(chat.id);
                                    }
                                }
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                searchResultMessages.clear();
                                for (TLRPC.Message message : res.messages) {
                                    searchResultMessages.add(new MessageObject(message, null, 0));
                                }
                                FileLog.e("tsupportSearch", "Chats: " + res.chats.size());
                                FileLog.e("tsupportSearch", "Users: " + res.users.size());
                                updateSearchResults(resultArray, resultArrayNames, new ArrayList<TLRPC.User>());
                            }
                        }
                        if (delegate != null) {
                            delegate.searchStateChanged(false);
                        }
                        reqId = 0;
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    private void searchDialogsInternal(final String query, final boolean needEncrypted) {
        /*if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRpc(reqId, true);
            reqId = 0;
        }

        if (query == null || query.length() == 0) {
            updateSearchResults(new ArrayList<TLObject>(), new ArrayList<CharSequence>(), new ArrayList<TLRPC.User>());
            return;
        }
        final ArrayList<TLObject> resultArray = new ArrayList<TLObject>();
        final ArrayList<CharSequence> resultArrayNames = new ArrayList<CharSequence>();

        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 256;
        req.peer = new TLRPC.TL_inputPeerEmpty();
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        if (delegate != null) {
            delegate.searchStateChanged(true);
        }
        reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                for (TLRPC.Chat chat: res.chats) {
                                    resultArrayNames.add(Utilities.generateSearchName(chat.title, null, query));
                                    resultArray.add(chat);
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultArrayNames, new ArrayList<TLRPC.User>());
                        reqId = 0;
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);

        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<TLRPC.User> encUsers = new ArrayList<TLRPC.User>();
                    String q = query.trim().toLowerCase();
                    if (q.length() == 0) {
                        updateSearchResults(new ArrayList<TLObject>(), new ArrayList<CharSequence>(), new ArrayList<TLRPC.User>());
                        return;
                    }


                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalizedInternal("SELECT u.data, u.status, u.name FROM users as u");
                    while (cursor.next()) {
                        String name = cursor.stringValue(2);
                        String username = null;
                        int usernamePos = name.lastIndexOf(";;;");
                        if (usernamePos != -1) {
                            username = name.substring(usernamePos + 3);
                        }
                        int found = 0;
                        if (name.startsWith(q) || name.contains(" " + q)) {
                            found = 1;
                        } else if (username != null && username.startsWith(q)) {
                            found = 2;
                        }
                        if (found != 0) {
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                            if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                TLRPC.User user = (TLRPC.User) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                if (user.id != UserConfig.getClientUserId()) {
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(1);
                                    }
                                    if (found == 1) {
                                        resultArrayNames.add(Utilities.generateSearchName(user.first_name, user.last_name, q));
                                    } else {
                                        resultArrayNames.add(Utilities.generateSearchName("@" + user.username, null, "@" + q));
                                    }
                                    resultArray.add(user);
                                }
                            }
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                        }
                    }
                    cursor.dispose();

                    cursor = MessagesStorage.getInstance().getDatabase().queryFinalizedCache("SELECT data, name FROM chats");
                    while (cursor.next()) {
                        String name = cursor.stringValue(1);
                        if (name.startsWith(q) || name.contains(" " + q)) {
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                            if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                TLRPC.Chat chat = (TLRPC.Chat) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                if (!needEncrypted && chat.id < 0) {
                                    continue;
                                }
                                resultArrayNames.add(Utilities.generateSearchName(chat.title, null, q));
                                resultArray.add(chat);
                            }
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                        }
                    }
                    cursor.dispose();
                    updateSearchResults(resultArray, resultArrayNames, encUsers);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });*/
    }

    private void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers) {
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (TLObject obj : result) {
                    if (obj instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) obj;
                        MessagesController.getInstance().putUser(user, true);
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        MessagesController.getInstance().putChat(chat, true);
                    } else if (obj instanceof TLRPC.EncryptedChat) {
                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                        MessagesController.getInstance().putEncryptedChat(chat, true);
                    }
                }
                for (TLRPC.User user : encUsers) {
                    MessagesController.getInstance().putUser(user, true);
                }
                searchResult = result;
                searchResultNames = names;
                notifyDataSetChanged();
            }
        });
    }

    public String getLastSearchText() {
        return lastSearchTextMessages;
    }

    public boolean isGlobalSearch(int i) {
        if (currentSearchType != 2) {
            int localCount = searchResult.size();
            int globalCount = globalSearch.size();
            if (i >= 0 && i < localCount) {
                return false;
            } else if (i > localCount && i <= globalCount + localCount) {
                return true;
            }
        }
        return false;
    }

    public void searchDialogs(final String query, final int type) {
        String lastSearchText;
        lastSearchText = lastSearchTextMessages;
        boolean typeChanged = currentSearchType != type;
        currentSearchType = type;
        if (query == null && lastSearchText == null || query != null && lastSearchText != null && query.equals(lastSearchText)) {
            if (typeChanged) {
                notifyDataSetChanged();
            }
            return;
        }
        lastSearchTextMessages = query;
        try {
            if (searchTimer != null) {
                searchTimer.cancel();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (query == null || query.length() == 0) {
            searchMessagesInternal(null);
            searchResult.clear();
            searchResultNames.clear();
            notifyDataSetChanged();
        } else {
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            searchMessagesInternal(query);
                        }
                    });
                }
            }, 200, 300);
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        return currentSearchType == 2 || i != searchResult.size();
    }

    @Override
    public int getCount() {
        if (currentSearchType == 2) {
            return searchResultMessages.size();
        } else {
            int count = searchResult.size();
            int globalCount = globalSearch.size();
            if (globalCount != 0) {
                count += globalCount + 1;
            }
            return count;
        }
    }

    @Override
    public Object getItem(int i) {
        if (currentSearchType == 2) {
            if (i < 0 || i >= searchResultMessages.size()) {
                return null;
            }
            return searchResultMessages.get(i);
        } else {
            int localCount = searchResult.size();
            int globalCount = globalSearch.size();
            if (i >= 0 && i < localCount) {
                return searchResult.get(i);
            } else if (i > localCount && i <= globalCount + localCount) {
                return globalSearch.get(i - localCount - 1);
            }
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        int type = getItemViewType(i);

        if (type == 1) {
            if (view == null) {
                view = new SettingsSectionLayout(mContext);
                ((SettingsSectionLayout) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                view.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
            }
        } else if (type == 0) {
            if (view == null) {
                view = new ChatOrUserCell(mContext);
            }

            TLRPC.User user = null;
            TLRPC.Chat chat = null;
            TLRPC.EncryptedChat encryptedChat = null;

            ((ChatOrUserCell) view).useSeparator = (i != getCount() - 1 && i != searchResult.size() - 1);
            Object obj = getItem(i);
            if (obj instanceof TLRPC.User) {
                user = MessagesController.getInstance().getUser(((TLRPC.User) obj).id);
                if (user == null) {
                    user = (TLRPC.User) obj;
                }
            } else if (obj instanceof TLRPC.Chat) {
                chat = MessagesController.getInstance().getChat(((TLRPC.Chat) obj).id);
            } else if (obj instanceof TLRPC.EncryptedChat) {
                encryptedChat = MessagesController.getInstance().getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
                user = MessagesController.getInstance().getUser(encryptedChat.user_id);
            }

            CharSequence username = null;
            CharSequence name = null;
            if (i < searchResult.size()) {
                name = searchResultNames.get(i);
                if (name != null && user != null && user.username != null && user.username.length() > 0) {
                    if (name.toString().startsWith("@" + user.username)) {
                        username = name;
                        name = null;
                    }
                }
            } else if (i > searchResult.size() && user != null && user.username != null) {
                try {
                    username = Html.fromHtml(String.format("<font color=\"#357aa8\">@%s</font>%s", user.username.substring(0, lastFoundUsername.length()), user.username.substring(lastFoundUsername.length())));
                } catch (Exception e) {
                    username = user.username;
                    FileLog.e("tmessages", e);
                }
            }

            ((ChatOrUserCell) view).setData(user, chat, encryptedChat, name, username);
        } else if (type == 2) {
            if (view == null) {
                view = new DialogCell(mContext);
            }
            ((DialogCell) view).useSeparator = (i != getCount() - 1);
            MessageObject messageObject = searchResultMessages.get(i);
            ((DialogCell) view).setDialog(messageObject.getDialogId(), messageObject, false, messageObject.messageOwner.date, 0);
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (currentSearchType == 2) {
            return 2;
        } else {
            if (i == searchResult.size()) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        if (currentSearchType == 2) {
            return searchResultMessages.isEmpty();
        }
        return searchResult.isEmpty() && globalSearch.isEmpty();
    }
}
