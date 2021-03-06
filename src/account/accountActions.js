/* @flow strict-local */
import type { Action } from '../types';
import {
  ACCOUNT_SWITCH,
  REALM_ADD,
  ACCOUNT_REMOVE,
  LOGIN_SUCCESS,
  LOGOUT,
} from '../actionConstants';
import type { ZulipVersion } from '../utils/zulipVersion';

export const accountSwitch = (index: number): Action => ({
  type: ACCOUNT_SWITCH,
  index,
});

export const realmAdd = (
  realm: URL,
  zulipFeatureLevel: number,
  zulipVersion: ZulipVersion,
): Action => ({
  type: REALM_ADD,
  realm,
  zulipFeatureLevel,
  zulipVersion,
});

export const removeAccount = (index: number): Action => ({
  type: ACCOUNT_REMOVE,
  index,
});

export const loginSuccess = (realm: URL, email: string, apiKey: string): Action => ({
  type: LOGIN_SUCCESS,
  realm,
  email,
  apiKey,
});

export const logout = (): Action => ({
  type: LOGOUT,
});
