import updateIcon from '@jetbrains/icons/update';
import Button from '@jetbrains/ring-ui/components/button/button';
import {React} from '@jetbrains/teamcity-api';

type OwnProps = {
  title?: string,
  onClick: () => Promise<void>
}

export default function UpdateButton({title, onClick}: OwnProps) {
  return (
    <Button
      title={title}
      icon={updateIcon}
      primary
      onClick={onClick}
    />
  );
}
