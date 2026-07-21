package com.myproxy.app.data

import android.content.Context
import com.myproxy.app.model.ProxyNode
import kotlinx.coroutines.flow.Flow

class NodeRepository private constructor(
    private val nodeDao: NodeDao,
) {
    // Repository 只封装节点表访问，不打印节点密码、UUID 或订阅来源。
    fun observeAll(): Flow<List<ProxyNode>> = nodeDao.observeAll()

    suspend fun insert(node: ProxyNode): Long = nodeDao.insert(node)

    suspend fun insertAll(nodes: List<ProxyNode>): List<Long> = nodeDao.insertAll(nodes)

    suspend fun update(node: ProxyNode) = nodeDao.update(node)

    suspend fun delete(node: ProxyNode) = nodeDao.delete(node)

    suspend fun deleteById(id: Long) = nodeDao.deleteById(id)

    suspend fun clear() = nodeDao.clear()

    suspend fun getById(id: Long): ProxyNode? = nodeDao.getById(id)

    companion object {
        @Volatile
        private var instance: NodeRepository? = null

        fun getInstance(context: Context): NodeRepository {
            return instance ?: synchronized(this) {
                instance ?: NodeRepository(
                    AppDatabase.getInstance(context).nodeDao(),
                ).also { instance = it }
            }
        }
    }
}
