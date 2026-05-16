package com.atguigu.order.mapper;

import com.atguigu.order.bean.LogisticsTrace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 物流轨迹Mapper接口
 */
@Mapper
public interface LogisticsTraceMapper extends BaseMapper<LogisticsTrace> {
    
    /**
     * 根据物流ID查询轨迹列表
     * @param logisticsId 物流ID
     * @return 轨迹列表
     */
    @Select("SELECT * FROM logistics_trace WHERE logistics_id = #{logisticsId} ORDER BY trace_time DESC")
    List<LogisticsTrace> selectByLogisticsId(@Param("logisticsId") Long logisticsId);
    
    /**
     * 插入物流轨迹
     * @param trace 物流轨迹
     * @return 影响行数
     */
    @Insert("INSERT INTO logistics_trace (logistics_id, trace_time, status, location, description, operator, create_time) " +
            "VALUES (#{logisticsId}, #{traceTime}, #{status}, #{location}, #{description}, #{operator}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertLogisticsTrace(LogisticsTrace trace);
    
    /**
     * 批量插入物流轨迹
     * @param traces 轨迹列表
     * @return 影响行数
     */
    @Insert("<script>" +
            "INSERT INTO logistics_trace (logistics_id, trace_time, status, location, description, operator, create_time) VALUES " +
            "<foreach item='trace' collection='traces' separator=','>" +
            "(#{trace.logisticsId}, #{trace.traceTime}, #{trace.status}, #{trace.location}, #{trace.description}, #{trace.operator}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsertLogisticsTrace(@Param("traces") List<LogisticsTrace> traces);
    
    /**
     * 根据物流ID删除轨迹
     * @param logisticsId 物流ID
     * @return 影响行数
     */
    @Delete("DELETE FROM logistics_trace WHERE logistics_id = #{logisticsId}")
    int deleteByLogisticsId(@Param("logisticsId") Long logisticsId);
}
