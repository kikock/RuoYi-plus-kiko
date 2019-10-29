package com.zebra.framework.aspectj;

import java.lang.reflect.Method;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.zebra.common.annotation.Log;
import com.zebra.common.config.ConfigServerApplication;
import com.zebra.common.enums.BusinessStatus;
import com.zebra.common.enums.BusinessType;
import com.zebra.common.exception.DemoModeException;
import com.zebra.common.exception.LimitIpException;
import com.zebra.common.json.JSON;
import com.zebra.common.utils.ServletUtils;
import com.zebra.common.utils.StringUtils;
import com.zebra.framework.manager.AsyncManager;
import com.zebra.framework.manager.factory.AsyncFactory;
import com.zebra.framework.util.ShiroUtils;
import com.zebra.system.domain.SysOperLog;
import com.zebra.system.domain.SysUser;

import lombok.extern.slf4j.Slf4j;

/**
 * 操作日志记录处理
 *
 * @author ruoyi
 */
@Aspect
@Component
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class LogAspect {

    @Autowired
    private ConfigServerApplication configServerApplication;

    // 配置织入点
    @Pointcut("@annotation(com.zebra.common.annotation.Log)")
    public void logPointCut() {
    }

    /**
     * 处理前执行
     *
     * @param joinPoint 切点
     */
    @Before(value = "logPointCut()")
    public void Before(JoinPoint joinPoint) {
        try {
            String ip = ShiroUtils.getIp();
            if (configServerApplication.getIps().contains(ip)) {
                log.info("[信息]该用户地址以及被限制，操作ip:" + ShiroUtils.getIp());
                throw new LimitIpException();
            }
            // 获得注解
            Log controllerLog = getAnnotationLog(joinPoint);
            if (controllerLog == null) {
                return;
            }
            String businessType = controllerLog.businessType().toString();
            if (Boolean.parseBoolean(configServerApplication.getDemoEnabled())) {
                if (BusinessType.DELETE.toString().equals(businessType)
                        || BusinessType.INSERT.toString().equals(businessType)
                        || BusinessType.UPDATE.toString().equals(businessType)
                        || BusinessType.GRANT.toString().equals(businessType)
                        || BusinessType.FORCE.toString().equals(businessType)
                        || BusinessType.CLEAN.toString().equals(businessType)
                        || BusinessType.SYNC.toString().equals(businessType)) {
                    log.info("[信息]演示模式下不允许操作，操作ip:" + ShiroUtils.getIp());
                    throw new DemoModeException();
                }
            }
        } catch (Exception ex) {
            if (ex instanceof DemoModeException) {
                throw new DemoModeException();
            }
            if (ex instanceof LimitIpException) {
                throw new DemoModeException();
            }
            // 记录本地异常日志
            log.error("==前置通知异常==");
            log.error("异常信息:{}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 是否存在注解，如果存在就获取
     */
    private Log getAnnotationLog(JoinPoint joinPoint) throws Exception {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        if (method != null) {
            return method.getAnnotation(Log.class);
        }
        return null;
    }

    /**
     * 处理完请求后执行
     *
     * @param joinPoint 切点
     */
    @AfterReturning(pointcut = "logPointCut()")
    public void doAfterReturning(JoinPoint joinPoint) {
        handleLog(joinPoint, null);
    }

    protected void handleLog(final JoinPoint joinPoint, final Exception e) {
        try {
            // 获得注解
            Log controllerLog = getAnnotationLog(joinPoint);
            if (controllerLog == null) {
                return;
            }

            // 获取当前的用户
            SysUser currentUser = ShiroUtils.getSysUser();

            // *========数据库日志=========*//
            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(BusinessStatus.SUCCESS.ordinal());
            // 请求的地址
            String ip = ShiroUtils.getIp();
            operLog.setOperIp(ip);

            operLog.setOperUrl(ServletUtils.getRequest().getRequestURI());
            if (currentUser != null) {
                operLog.setOperName(currentUser.getLoginName());
                if (StringUtils.isNotNull(currentUser.getDept())
                        && StringUtils.isNotEmpty(currentUser.getDept().getDeptName())) {
                    operLog.setDeptName(currentUser.getDept().getDeptName());
                }
            }

            if (e != null) {
                operLog.setStatus(BusinessStatus.FAIL.ordinal());
                operLog.setErrorMsg(StringUtils.substring(e.getMessage(), 0, 2000));
            }
            // 设置方法名称
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            operLog.setMethod(className + "." + methodName + "()");
            // 处理设置注解上的参数
            getControllerMethodDescription(controllerLog, operLog);
            // 保存数据库
            AsyncManager.me().execute(AsyncFactory.recordOper(operLog));
        } catch (Exception exp) {
            // 记录本地异常日志
            log.error("==前置通知异常==");
            log.error("异常信息:{}", exp.getMessage());
            exp.printStackTrace();
        }
    }

    /**
     * 获取注解中对方法的描述信息 用于Controller层注解
     *
     * @param log     日志
     * @param operLog 操作日志
     * @throws Exception
     */
    public void getControllerMethodDescription(Log log, SysOperLog operLog) throws Exception {
        // 设置action动作
        operLog.setBusinessType(log.businessType().ordinal());
        // 设置标题
        operLog.setTitle(log.title());
        // 设置操作人类别
        operLog.setOperatorType(log.operatorType().ordinal());
        // 是否需要保存request，参数和值
        if (log.isSaveRequestData()) {
            // 获取参数的信息，传入到数据库中。
            setRequestValue(operLog);
        }
    }

    /**
     * 获取请求的参数，放到log中
     *
     * @param operLog 操作日志
     * @throws Exception 异常
     */
    private void setRequestValue(SysOperLog operLog) throws Exception {
        Map<String, String[]> map = ServletUtils.getRequest().getParameterMap();
        String params = JSON.marshal(map);
        operLog.setOperParam(StringUtils.substring(params, 0, 2000));
    }

    /**
     * 拦截异常操作
     *
     * @param joinPoint 切点
     * @param e         异常
     */
    @AfterThrowing(value = "logPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e);
    }
}
