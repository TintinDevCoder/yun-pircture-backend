package com.dd.yunpicturebackend.api.aliyunai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.dd.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.dd.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.dd.yunpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {
    //读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    // 创建任务
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.OPERATION_ERROR, "扩图参数为空");
        //发送请求
        HttpRequest request = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        // 获取响应
        try (HttpResponse httpResponse = request.execute()) {
            ThrowUtils.throwIf(!httpResponse.isOk(), ErrorCode.OPERATION_ERROR, "接口调用失败");
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (response.getCode() != null) {
                String errorMessage = response.getMessage();
                log.error("创建扩图任务失败:{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI扩图失败" + errorMessage);
            }
            return response;
        } catch (Exception e) {
            log.error("创建扩图任务失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI扩图失败" + e.getMessage());
        }
    }
    // 查询创建的任务结果
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(taskId == null, ErrorCode.OPERATION_ERROR, "任务ID为空");
        //发送请求
        HttpRequest request = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header("Authorization", "Bearer " + apiKey);
        // 获取响应
        try (HttpResponse httpResponse = request.execute()) {
            ThrowUtils.throwIf(!httpResponse.isOk(), ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            GetOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
            return response;
        } catch (Exception e) {
            log.error("查询扩图任务失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "查询扩图任务失败" + e.getMessage());
        }
    }
}
