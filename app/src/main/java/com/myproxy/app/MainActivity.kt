package com.myproxy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.myproxy.app.ui.AppNavRoot
import com.myproxy.app.ui.theme.MyProxyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 正常入口加载首页、配置、导入和设置导航；连接配置由选中节点动态生成。
        setContent {
            MyProxyTheme {
                AppNavRoot()
            }
        }
    }
}
