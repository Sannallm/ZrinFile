package com.zrinfile.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.io.FileOutputStream;

/** HTML 源码编辑。 */
public class HtmlEditorActivity extends AppCompatActivity {

    private EditText edit;
    private TextView save;
    private TextView preview;
    private File file;
    private String encoding = "UTF-8";
    private boolean dirty;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        edit = findViewById(R.id.html_edit);
        save = findViewById(R.id.html_save);
        preview = findViewById(R.id.html_preview);

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        toolbar.setTitle(file.getName());
        toolbar.setSubtitle("源码编辑");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmExit();
            }
        });

        edit.setTextSize(13);
        edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (loaded) dirty = true;
            }
        });

        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final FileUtils.TextResult result = FileUtils.readText(file, 8 * 1024 * 1024);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        encoding = result.encoding == null ? "UTF-8" : result.encoding;
                        edit.setText(result.text);
                        loaded = true;
                        dirty = false;
                        if (result.error != null) UiUtils.toast(HtmlEditorActivity.this, "读取失败：" + result.error);
                    }
                });
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFile(false);
            }
        });
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFile(true);
            }
        });
    }

    private void saveFile(final boolean thenPreview) {
        final String text = edit.getText().toString();
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(file);
                    out.write(text.getBytes(encoding));
                } catch (Exception e) {
                    error = e.getMessage();
                } finally {
                    FileUtils.closeQuietly(out);
                }
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (err != null) {
                            UiUtils.toast(HtmlEditorActivity.this, "保存失败：" + err);
                            return;
                        }
                        dirty = false;
                        UiUtils.toast(HtmlEditorActivity.this, "已保存");
                        if (thenPreview) {
                            Intent i = new Intent(HtmlEditorActivity.this, HtmlViewerActivity.class);
                            i.putExtra("path", file.getAbsolutePath());
                            startActivity(i);
                        }
                    }
                });
            }
        });
    }

    private void confirmExit() {
        if (!dirty) {
            finish();
            return;
        }
        UiUtils.confirm(this, "放弃修改", "内容尚未保存，确定退出吗？", new Runnable() {
            @Override
            public void run() {
                dirty = false;
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }
}
