package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 通用控制器
 *
 * 【本地改造】完整版用阿里云 OSS 存储图片，需要真实 OSS 账号。
 * 这里改为保存到本机磁盘，并通过 WebMvcConfiguration 的 /images/** 静态映射对外提供访问，
 * 不依赖任何云服务即可完成图片上传功能。
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    // 图片本地存储目录
    private static final String UPLOAD_DIR = "E:/BaiduNetdiskDownload/images/";
    // 图片访问地址前缀（由本服务的静态资源映射提供）
    private static final String URL_PREFIX = "http://localhost:8080/images/";

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file);

        // 取原始文件的扩展名
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') != -1) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        // 用 UUID 生成唯一文件名，避免同名文件互相覆盖
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;

        try {
            // 目录不存在则创建，否则 transferTo 会抛 IOException
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 保存文件到本地
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            log.error("{}：{}", MessageConstant.UPLOAD_FAILED, originalFilename, e);
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }

        // 返回浏览器可直接访问的图片地址
        String url = URL_PREFIX + fileName;
        log.info("文件上传成功：{}", url);
        return Result.success(url);
    }
}
