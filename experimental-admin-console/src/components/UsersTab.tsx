import { useEffect, useState, useCallback } from 'react';

interface EnvironmentUser {
  id: string;
  username: string;
  grantedAt: string;
}

interface UsersTabProps {
  domainId: string;
  solutionId: string;
  envId: string;
  authFetch: (path: string, opts?: RequestInit) => Promise<Response | null>;
}

export default function UsersTab({ domainId, solutionId, envId, authFetch }: UsersTabProps) {
  const [users, setUsers] = useState<EnvironmentUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<EnvironmentUser | null>(null);
  const [removing, setRemoving] = useState(false);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [resetPasswordUser, setResetPasswordUser] = useState<EnvironmentUser | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [resettingPassword, setResettingPassword] = useState(false);
  const [resetPasswordError, setResetPasswordError] = useState<string | null>(null);
  const [resetPasswordSuccess, setResetPasswordSuccess] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/environments/${envId}/admin-users`);
      if (!res) { setLoading(false); return; }
      if (!res.ok) {
        setError(`Failed to fetch users (${res.status})`);
        setLoading(false);
        return;
      }
      const data = await res.json();
      setUsers(Array.isArray(data) ? data : []);
    } catch {
      setError('Failed to fetch users');
    }
    setLoading(false);
  }, [domainId, solutionId, envId, authFetch]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const formatDate = (iso: string) => {
    const d = new Date(iso);
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  };

  const clearForm = () => {
    setUsername('');
    setPassword('');
    setFormError(null);
  };

  const handleCancel = () => {
    setShowAddForm(false);
    clearForm();
  };

  const grantAccess = async (userId: string): Promise<{ ok: boolean; status: number; message?: string }> => {
    const res = await authFetch(`/${domainId}/${solutionId}/admin-users/${userId}/environments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ environmentId: envId }),
    });
    if (!res) return { ok: false, status: 0, message: 'Request failed' };
    if (res.ok) return { ok: true, status: res.status };
    if (res.status === 409) return { ok: false, status: 409, message: 'User already has access to this environment' };
    const body = await res.json().catch(() => null);
    const message = body?.error?.message || `Grant access failed (${res.status})`;
    return { ok: false, status: res.status, message };
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;

    setSubmitting(true);
    setFormError(null);

    try {
      // Step 1: Create user
      const createRes = await authFetch('/admin-users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          password,
          domainId,
          solutionId,
        }),
      });

      if (!createRes) {
        setFormError('Request failed');
        setSubmitting(false);
        return;
      }

      let userId: string | null = null;

      if (createRes.ok) {
        // User created successfully — extract ID
        const created = await createRes.json();
        userId = created.id;
      } else if (createRes.status === 409) {
        // Duplicate username — look up existing user
        const lookupRes = await authFetch(`/admin-users?domainId=${domainId}&solutionId=${solutionId}`);
        if (!lookupRes || !lookupRes.ok) {
          setFormError('User already exists but failed to look up existing user');
          setSubmitting(false);
          return;
        }
        const allUsers = await lookupRes.json();
        const existing = (Array.isArray(allUsers) ? allUsers : []).find(
          (u: { username: string }) => u.username === username.trim()
        );
        if (!existing) {
          setFormError('A user with this username already exists');
          setSubmitting(false);
          return;
        }
        userId = existing.id;
      } else {
        // Other create error
        const body = await createRes.json().catch(() => null);
        setFormError(body?.error?.message || `Failed to create user (${createRes.status})`);
        setSubmitting(false);
        return;
      }

      // Step 2: Grant access
      const grantResult = await grantAccess(userId!);
      if (!grantResult.ok) {
        setFormError(grantResult.message || 'Failed to grant access');
        setSubmitting(false);
        return;
      }

      // Both succeeded — close form, clear inputs, refresh
      setShowAddForm(false);
      clearForm();
      await fetchUsers();
    } catch {
      setFormError('An unexpected error occurred');
    }
    setSubmitting(false);
  };

  const handleRemoveUser = async () => {
    if (!confirmRemove) return;
    setRemoving(true);
    setRemoveError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/admin-users/${confirmRemove.id}/environments/${envId}`,
        { method: 'DELETE' }
      );
      if (!res) {
        setRemoveError('Request failed');
        setRemoving(false);
        return;
      }
      if (res.ok) {
        setUsers((prev) => prev.filter((u) => u.id !== confirmRemove.id));
        setConfirmRemove(null);
      } else {
        const body = await res.json().catch(() => null);
        setRemoveError(body?.error?.message || `Failed to remove user (${res.status})`);
      }
    } catch {
      setRemoveError('An unexpected error occurred');
    }
    setRemoving(false);
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetPasswordUser || newPassword.length < 8) return;
    setResettingPassword(true);
    setResetPasswordError(null);
    try {
      const res = await authFetch(`/admin-users/${resetPasswordUser.id}/password`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: newPassword }),
      });
      if (!res) {
        setResetPasswordError('Request failed');
        setResettingPassword(false);
        return;
      }
      if (res.ok) {
        setResetPasswordUser(null);
        setNewPassword('');
        setResetPasswordSuccess(`Password reset for ${resetPasswordUser.username}`);
        setTimeout(() => setResetPasswordSuccess(null), 3000);
      } else {
        const body = await res.json().catch(() => null);
        setResetPasswordError(body?.error?.message || `Failed to reset password (${res.status})`);
      }
    } catch {
      setResetPasswordError('An unexpected error occurred');
    }
    setResettingPassword(false);
  };

  if (loading) {
    return <p className="text-gray-500 text-sm">Loading users...</p>;
  }

  return (
    <div>
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
          {error}
        </div>
      )}

      {resetPasswordSuccess && (
        <div className="bg-green-50 border border-green-200 text-green-700 text-sm rounded px-4 py-3 mb-4">
          {resetPasswordSuccess}
        </div>
      )}

      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-semibold text-gray-800">Environment Users</h2>
        <div className="flex gap-2">
          <button
            onClick={() => setShowAddForm(true)}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Add User
          </button>
          <button
            onClick={fetchUsers}
            className="px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded hover:bg-gray-50"
          >
            Refresh
          </button>
        </div>
      </div>

      {showAddForm && (
        <form onSubmit={handleSubmit} className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
          <h3 className="text-sm font-medium text-gray-800 mb-3">Add User</h3>
          {formError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-3 py-2 mb-3">
              {formError}
            </div>
          )}
          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label htmlFor="add-user-username" className="block text-sm text-gray-700 mb-1">Username</label>
              <input
                id="add-user-username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                placeholder="Enter username"
                disabled={submitting}
              />
            </div>
            <div className="flex-1">
              <label htmlFor="add-user-password" className="block text-sm text-gray-700 mb-1">Password</label>
              <input
                id="add-user-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                placeholder="Enter password"
                disabled={submitting}
              />
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={!username.trim() || !password.trim() || submitting}
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? 'Adding...' : 'Add'}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                disabled={submitting}
                className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </div>
        </form>
      )}

      {users.length === 0 && !error ? (
        <p className="text-gray-500 text-sm">No users have access to this environment.</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="text-left px-4 py-3 font-medium text-gray-700">Username</th>
                <th className="text-left px-4 py-3 font-medium text-gray-700">Granted At</th>
                <th className="text-right px-4 py-3 font-medium text-gray-700">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className="border-b border-gray-100 last:border-b-0">
                  <td className="px-4 py-3 text-gray-800">{user.username}</td>
                  <td className="px-4 py-3 text-gray-600">{formatDate(user.grantedAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => { setResetPasswordUser(user); setNewPassword(''); setResetPasswordError(null); }}
                      className="text-blue-500 hover:text-blue-700 text-sm mr-3"
                    >
                      Reset Password
                    </button>
                    <button
                      onClick={() => setConfirmRemove(user)}
                      className="text-red-500 hover:text-red-700 text-sm"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {confirmRemove && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg border border-gray-200 p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold text-gray-800 mb-2">Remove User Access</h3>
            <p className="text-sm text-gray-600 mb-1">
              Are you sure you want to remove <span className="font-medium text-gray-800">{confirmRemove.username}</span> from this environment?
            </p>
            <p className="text-sm text-gray-500 mb-4">
              This only removes access to this environment. The user account will not be deleted.
            </p>
            {removeError && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-3 py-2 mb-4">
                {removeError}
              </div>
            )}
            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setConfirmRemove(null); setRemoveError(null); }}
                disabled={removing}
                className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleRemoveUser}
                disabled={removing}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {removing ? 'Removing...' : 'Remove'}
              </button>
            </div>
          </div>
        </div>
      )}

      {resetPasswordUser && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg border border-gray-200 p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold text-gray-800 mb-2">
              Reset Password for {resetPasswordUser.username}
            </h3>
            <form onSubmit={handleResetPassword}>
              {resetPasswordError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-3 py-2 mb-3">
                  {resetPasswordError}
                </div>
              )}
              <div className="mb-4">
                <label htmlFor="reset-password-input" className="block text-sm text-gray-700 mb-1">New Password</label>
                <input
                  id="reset-password-input"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  placeholder="Enter new password (min 8 characters)"
                  disabled={resettingPassword}
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => { setResetPasswordUser(null); setNewPassword(''); setResetPasswordError(null); }}
                  disabled={resettingPassword}
                  className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={newPassword.length < 8 || resettingPassword}
                  className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {resettingPassword ? 'Resetting...' : 'Reset'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
