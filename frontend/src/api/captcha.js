/**
 * 验证码接口：刻意不走 http 拦截器（避免 401 时误判为"登录过期"），
 * 因为登录前调用本接口，账号本来就未登录。
 *
 * 兼容旧调用方：仍返回 { data: { key, image, expireSec } } 的形态。
 */
export const getCaptcha = async () => {
  let resp
  try {
    resp = await fetch('/api/captcha', {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
  } catch (netErr) {
    throw new Error('验证码网络请求失败：后端可能未启动或不可达 (' + netErr.message + ')')
  }
  if (!resp.ok) {
    throw new Error('验证码接口异常 HTTP ' + resp.status + '：请确认后端已重启加载新版本（含 CaptchaController）')
  }
  let body
  try {
    body = await resp.json()
  } catch (e) {
    throw new Error('验证码响应不是 JSON：可能是反代或后端版本不一致')
  }
  if (body && body.code !== 200) {
    if (body.code === 401) {
      throw new Error('验证码接口被认证拦截 - 后端为旧版本未加载 /api/captcha 白名单。请重启后端后重试')
    }
    throw new Error('验证码接口业务错误 [' + body.code + ']：' + (body.message || '未知错误'))
  }
  return body
}
