package com.myproxy.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myproxy.app.model.ProxyNode
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    // 写入单个节点，返回 Room 生成的本地主键。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: ProxyNode): Long

    // 批量写入节点，用于订阅导入；调用方不得记录原始分享链接。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<ProxyNode>): List<Long>

    // 更新已存在节点，敏感字段只保存在本地数据库。
    @Update
    suspend fun update(node: ProxyNode)

    // 按实体删除节点。
    @Delete
    suspend fun delete(node: ProxyNode)

    // 按本地主键删除节点。
    @Query("DELETE FROM proxy_nodes WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 清空本地节点表，不影响其他设置数据。
    @Query("DELETE FROM proxy_nodes")
    suspend fun clear()

    // 持续观察节点列表，供界面自动刷新。
    @Query("SELECT * FROM proxy_nodes ORDER BY id DESC")
    fun observeAll(): Flow<List<ProxyNode>>

    // 按本地主键读取单个节点。
    @Query("SELECT * FROM proxy_nodes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProxyNode?
}
