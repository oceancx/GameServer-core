/**
 * 
 */
package core.fire.executor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.GeneratedMessage;

import core.fire.Component;
import core.fire.Config;
import core.fire.NamedThreadFactory;
import core.fire.net.NetSession;
import core.fire.net.netty.Packet;
import core.fire.util.BaseUtil;
import core.fire.util.ClassUtil;

/**
 * 事件派发处理器，负责将网络IO传过来的消息分发给指定处理器处理
 * 
 * @author lhl
 *
 *         2016年1月30日 下午3:49:52
 */
@org.springframework.stereotype.Component
public final class DispatcherHandler implements Handler, Component
{
    private static final Logger LOG = LoggerFactory.getLogger(DispatcherHandler.class);
    // <指令，处理器>
    private Map<Short, Handler> handlerMap;
    // <指令，请求参数类型>
    private Map<Short, GeneratedMessage> requestParamType;
    // 指令处理线程池
    private ExecutorService executor;
    // 消息队列将被作为附件设置到NetSession上
    // 绑定了消息队列的session的事件将被提交到消息队列执行，否则统一提交到公用消息队列
    public static final String SEQUENCE_KEY = "SEQUENCE_KEY";

    public DispatcherHandler() {
        handlerMap = new HashMap<>();
        requestParamType = new HashMap<>();
        executor = Executors.newFixedThreadPool(8, new NamedThreadFactory("LOGIC"));
    }

    /**
     * 该方法将在Netty I/O线程池中运行
     */
    @Override
    public void handle(NetSession session, Packet packet) {
        Handler handler = handlerMap.get(Short.valueOf(packet.code));
        if (handler == null) {
            LOG.warn("No handler found for code {}, session will be closed", packet.code);
            session.close();
            return;
        }

        submitTask(session, handler, packet);
    }

    /**
     * 将消息封装成任务提交到线程池执行
     * 
     * @param session
     * @param handler
     * @param packet
     */
    private void submitTask(NetSession session, Handler handler, Packet packet) {
        Object attachObj = session.getAttachment(SEQUENCE_KEY);

        if (attachObj != null || (attachObj instanceof Sequence)) {
            Sequence sequence = (Sequence) attachObj;
            sequence.enqueue(new RunnableTask(handler, session, packet, sequence));
        } else {
            executor.submit(() -> handler.handle(session, packet));
        }
    }

    /**
     * 注册指令处理器
     * 
     * @param code
     * @param handler
     * @return 若该指令已注册过则返回之前注册的处理器，否则返回null
     * @throws IllegalStateException
     */
    private void addHandler(short code, Handler handler) throws IllegalStateException {
        Handler oldHandler = handlerMap.put(Short.valueOf(code), handler);
        if (oldHandler != null) {
            throw new IllegalStateException("Duplicate handler for code " + code + ", old: "
                    + oldHandler.getClass().getName() + ", new: " + handler.getClass().getName());
        }
    }

    /**
     * 注册请求参数类型
     * 
     * @param code
     * @param param
     */
    private void addParamType(short code, GeneratedMessage param) {
        requestParamType.put(Short.valueOf(code), param);
    }

    /**
     * 获取请求参数类型
     * 
     * @param code
     * @return
     */
    public GeneratedMessage getParamType(short code) {
        return requestParamType.get(Short.valueOf(code));
    }

    /**
     * 生成新消息队列
     * 
     * @return
     */
    public Sequence newSequence() {
        return new Sequence(executor);
    }

    @Override
    public void start() throws Exception {
        loadHandler(Config.HANDLER_SCAN_PACKAGES);
        LOG.debug("DispatcherHandler start");
    }

    /**
     * 加载指令处理器
     * 
     * @param searchPackage 搜索包名，多个包名使用逗号分割
     * @throws Exception
     */
    private void loadHandler(String searchPackage) throws Exception {
        if (BaseUtil.isNullOrEmpty(searchPackage)) {
            return;
        }

        String[] packages = BaseUtil.split(searchPackage.trim(), ",");
        for (String onePackage : packages) {
            if (!BaseUtil.isNullOrEmpty(onePackage)) {
                LOG.debug("Load handler from package {}", onePackage);
                List<Class<?>> classList = ClassUtil.getClasses(onePackage);
                for (Class<?> handler : classList) {
                    RequestHandler annotation = handler.getAnnotation(RequestHandler.class);
                    if (annotation != null) {
                        short code = annotation.code();
                        Handler handlerInstance = (Handler) handler.newInstance();
                        addHandler(code, handlerInstance);
                        Class<? extends GeneratedMessage> paramType = annotation.requestParamType();
                        GeneratedMessage paramInstance = instantiate(paramType);
                        addParamType(code, paramInstance);
                    }
                }
            }
        }
    }

    // 每个生成的PB协议类都应该有一个getDefaultInstance静态方法
    private GeneratedMessage instantiate(Class<? extends GeneratedMessage> type) throws NoSuchMethodException,
            SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method method = type.getMethod("getDefaultInstance");
        return (GeneratedMessage) method.invoke(type);
    }

    @Override
    public void stop() {
        BaseUtil.shutdownThreadPool(executor, 5 * 1000);
        LOG.debug("DispatcherHandler stop");
    }
}
