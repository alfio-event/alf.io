import { addIcon } from '../svg/add';
import {checkIcon} from '../svg/check';
import {homeIcon} from '../svg/home';
import { organizationIcon } from '../svg/organization';
import { settingsIcon } from '../svg/settings';

const ICONS = [
  checkIcon,
  homeIcon,
  checkIcon,
  addIcon,
  organizationIcon,
  settingsIcon,
];
export const ICON_CONFIG = {
  sizes: {
    xs: '10px',
    sm: '12px',
    md: '16px',
    lg: '20px',
    xl: '25px',
    xxl: '30px',
  },
  defaultSize: 'xl',
  icons: ICONS
};
