/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Per article 5 of the Apache 2.0 License, some modifications to this code
 * were made by the Oneplus Project.
 *
 * Modifications Copyright (C) 2015 The Oneplus Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.android.documentsui;

import static com.android.documentsui.DocumentsActivity.TAG;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.documentsui.model.DocumentInfo;

import java.io.FileNotFoundException;

/**
 * Dialog to create a new directory.
 */
public class CreateDirectoryFragment extends DialogFragment {
    private static final String TAG_CREATE_DIRECTORY = "create_directory";

    public static void show(FragmentManager fm) {
        final CreateDirectoryFragment dialog = new CreateDirectoryFragment();
        dialog.show(fm, TAG_CREATE_DIRECTORY);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity();
        final ContentResolver resolver = context.getContentResolver();

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());

        final View view = dialogInflater.inflate(R.layout.dialog_create_dir, null, false);
        final EditText text1 = (EditText) view.findViewById(android.R.id.text1);
        text1.setFilters(new InputFilter[] { new FolderInputFilter() });

        builder.setTitle(R.string.menu_create_dir);
        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String displayName = text1.getText().toString();

                final DocumentsActivity activity = (DocumentsActivity) getActivity();

                if (displayName == null || "".equals(displayName.trim())) {
                    Toast.makeText(activity, activity.getResources().getString(
                            R.string.folder_name_empty, displayName), Toast.LENGTH_LONG).show();
                    return;
                }

                final DocumentInfo cwd = activity.getCurrentDirectory();

                boolean exists = DocumentsContract.isChildDocument(resolver, cwd.derivedUri,
                        displayName);
                if (exists) {
                    Toast.makeText(activity, activity.getResources().getString(
                            R.string.folder_exists, displayName), Toast.LENGTH_LONG).show();
                } else {
                    new CreateDirectoryTask(activity, cwd, displayName).executeOnExecutor(
                            ProviderExecutor.forAuthority(cwd.authority));
                }
                ((DocumentsActivity) context).removeDialog((Dialog) dialog);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DocumentsActivity) context).removeDialog((Dialog) dialog);
            }
        });

        AlertDialog dialog = builder.create();
        ((DocumentsActivity) context).addDialog((Dialog) dialog);
        return dialog;
    }

    private class CreateDirectoryTask extends AsyncTask<Void, Void, DocumentInfo> {
        private final DocumentsActivity mActivity;
        private final DocumentInfo mCwd;
        private final String mDisplayName;

        public CreateDirectoryTask(
                DocumentsActivity activity, DocumentInfo cwd, String displayName) {
            mActivity = activity;
            mCwd = cwd;
            mDisplayName = displayName;
        }

        @Override
        protected void onPreExecute() {
            mActivity.setPending(true);
        }

        @Override
        protected DocumentInfo doInBackground(Void... params) {
            final ContentResolver resolver = mActivity.getContentResolver();
            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, mCwd.derivedUri.getAuthority());
                final Uri childUri = DocumentsContract.createDocument(
                        client, mCwd.derivedUri, Document.MIME_TYPE_DIR, mDisplayName);
                return DocumentInfo.fromUri(resolver, childUri);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to create directory", e);
                return null;
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Failed to create directory", e);
                return null;
            } finally {
                ContentProviderClient.releaseQuietly(client);
            }
        }

        @Override
        protected void onPostExecute(DocumentInfo result) {
            if (result != null) {
                // Navigate into newly created child
                mActivity.onDocumentPicked(result);
            } else {
                Toast.makeText(mActivity, R.string.create_error, Toast.LENGTH_SHORT).show();
            }

            mActivity.setPending(false);
        }
    }
}
