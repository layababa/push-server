package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.DeviceBinding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {
    
    fun findByDeviceUuid(deviceUuid: String): Optional<DeviceBinding>
    
    fun findByUserId(userId: Long): List<DeviceBinding>
    
    // 查用户的所有设备uuid
    @Query("SELECT d.deviceUuid FROM DeviceBinding d WHERE d.userId = :userId")
    fun findDeviceUuidsByUserId(@Param("userId") userId: Long): List<String>
    
    // upsert逻辑: 存在就更新，不存在就插入
    // pgsql的ON CONFLICT太香了，但jpa不太好用，这里用代码实现
    @Modifying
    @Query("""
        UPDATE DeviceBinding d 
        SET d.userId = :userId, d.lastLoginIp = :ip, d.lastActive = CURRENT_TIMESTAMP 
        WHERE d.deviceUuid = :uuid
    """)
    fun updateBinding(
        @Param("uuid") deviceUuid: String, 
        @Param("userId") userId: Long, 
        @Param("ip") lastLoginIp: String?
    ): Int
}
