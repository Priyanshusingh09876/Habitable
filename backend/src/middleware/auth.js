const jwt = require('jsonwebtoken');

function signToken(user, env = process.env) {
  return jwt.sign({ sub: String(user.id), email: user.email }, env.JWT_SECRET, { expiresIn: env.JWT_EXPIRES_IN || '30d' });
}

/** Express middleware: requires `Authorization: Bearer <jwt>` and sets req.userId. */
function requireAuth(env = process.env) {
  return (req, res, next) => {
    const header = req.get('authorization') || '';
    const [scheme, token] = header.split(' ');
    if (scheme !== 'Bearer' || !token) return res.status(401).json({ error: 'missing bearer token' });
    try {
      const payload = jwt.verify(token, env.JWT_SECRET);
      req.userId = Number(payload.sub);
      req.userEmail = payload.email;
      return next();
    } catch (e) {
      return res.status(401).json({ error: 'invalid or expired token' });
    }
  };
}

module.exports = { signToken, requireAuth };
