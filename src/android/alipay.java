package cn.hhjjj.alipay;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.alipay.sdk.app.PayTask;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.widget.Toast;
import android.text.TextUtils;
import android.annotation.SuppressLint;

/**
 * This class echoes a string called from JavaScript.
 */
public class alipay extends CordovaPlugin {

    private static final int SDK_PAY_FLAG = 1;
    private static final Pattern SIGN_PATTERN = Pattern.compile("([&?]sign=)([^&]+)");

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("payment")) {
            String orderInfo = extractOrderInfo(args);
            if (TextUtils.isEmpty(orderInfo)) {
                callbackContext.error("invalid payInfo: expected order string or object with data/orderInfo field");
                return true;
            }
            this.payment(orderInfo, callbackContext);
            return true;
        }
        return false;
    }

    private void payment(String orderInfo, final CallbackContext callbackContext) {

        final String payInfo = normalizeOrderInfo(orderInfo);
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                PayTask alipay = new PayTask(cordova.getActivity());
                Map<String, String> result = alipay.payV2(payInfo, true);
                Log.i("msp", result.toString());

                Message msg = new Message();
                msg.what = SDK_PAY_FLAG;
                msg.obj = result;
                mHandler.sendMessage(msg);

                PayResult payResult = new PayResult(result);
                String resultInfo = payResult.getResult();// 同步返回需要验证的信息
                String resultStatus = payResult.getResultStatus();
                // 判断resultStatus 为9000则代表支付成功
                if (TextUtils.equals(resultStatus, "9000")) {
                    // 该笔订单是否真实支付成功，需要依赖服务端的异步通知。
                    callbackContext.success(new JSONObject(result));
                } else {
                    // 该笔订单真实的支付结果，需要依赖服务端的异步通知。
                    callbackContext.error(new JSONObject(result));
                }
            }
        });

    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @SuppressWarnings("unused")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDK_PAY_FLAG: {
                    @SuppressWarnings("unchecked")
                    PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                    /**
                     对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                     */
                    String resultInfo = payResult.getResult();// 同步返回需要验证的信息
                    String resultStatus = payResult.getResultStatus();
                    // 判断resultStatus 为9000则代表支付成功
                    // 判断resultStatus 为9000则代表支付成功
                    if (TextUtils.equals(resultStatus, "9000")) {
                        // 该笔订单是否真实支付成功，需要依赖服务端的异步通知。
                        Toast.makeText(cordova.getActivity(), "支付成功" + resultStatus, Toast.LENGTH_SHORT);
                    } else {
                        // 该笔订单真实的支付结果，需要依赖服务端的异步通知。
                        Toast.makeText(cordova.getActivity(), "支付失败" + resultStatus, Toast.LENGTH_SHORT);
                    }
                    break;
                }
                default:
                    break;
            }
        }

        ;
    };

    private String extractOrderInfo(JSONArray args) throws JSONException {
        if (args == null || args.length() == 0) {
            return null;
        }

        Object raw = args.get(0);
        if (raw instanceof String) {
            return ((String) raw).trim();
        }

        if (raw instanceof JSONObject) {
            JSONObject json = (JSONObject) raw;
            String orderInfo = json.optString("data", null);
            if (TextUtils.isEmpty(orderInfo)) {
                orderInfo = json.optString("orderInfo", null);
            }
            if (TextUtils.isEmpty(orderInfo)) {
                orderInfo = json.optString("payInfo", null);
            }
            return orderInfo == null ? null : orderInfo.trim();
        }

        return String.valueOf(raw).trim();
    }

    private String normalizeOrderInfo(String orderInfo) {
        if (TextUtils.isEmpty(orderInfo)) {
            return orderInfo;
        }

        String normalized = orderInfo.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        Matcher matcher = SIGN_PATTERN.matcher(normalized);
        if (matcher.find()) {
            String rawSign = matcher.group(2);
            String fixedSign = rawSign.replace("+", "%2B");
            if (!rawSign.equals(fixedSign)) {
                normalized = normalized.substring(0, matcher.start(2))
                        + fixedSign
                        + normalized.substring(matcher.end(2));
            }
        }

        return normalized;
    }

}
