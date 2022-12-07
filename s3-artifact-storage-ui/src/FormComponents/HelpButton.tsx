import helpIcon from '@jetbrains/icons/help';
import Button from '@jetbrains/ring-ui/components/button/button';
import {React} from '@jetbrains/teamcity-api';

import {helpButton} from './styles.css';

type OwnProps = {
  title?: string,
  href: string,
}

export default function HelpButton({title = 'View help', href}: OwnProps) {
  return (
    <Button
      className={helpButton}
      title={title}
      icon={helpIcon}
      primary={false}
      href={href}
      onClick={event => {
        window.BS.Util.showHelp(event, href, {width: 0, height: 0});
        return false;
      }}
    />
  );
}
